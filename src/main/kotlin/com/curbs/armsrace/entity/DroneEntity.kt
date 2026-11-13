package com.curbs.armsrace.entity

import com.curbs.armsrace.item.ControllerItem
import com.curbs.armsrace.api.uav.UavControlInput
import com.curbs.armsrace.api.uav.UavPredictionEngine
import com.curbs.armsrace.api.uav.UavPredictionState
import com.curbs.armsrace.api.uav.UavLinkable
import com.curbs.armsrace.api.uav.UavInputReceiver
import com.curbs.armsrace.api.uav.UavPilot
import com.julian.createwarfare.effects.server.ShakeEffect
import com.julian.createwarfare.effects.server.SoundWaveEffect
import com.julian.createwarfare.effects.server.WaveEffect
import com.julian.createwarfare.effects.server.particles.GenericExplosionParticleEffect
import com.julian.createwarfare.effects.server.particles.GroundExplosionParticleEffect
import com.julian.createwarfare.explosions.types.GenericExplosion
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.*

data class DroneInputPayload(
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
    val seq: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DroneInputPayload> {
        return TYPE
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<DroneInputPayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "drone_input")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, DroneInputPayload> = object : StreamCodec<ByteBuf, DroneInputPayload> {
            override fun encode(buf: ByteBuf, v: DroneInputPayload) {
                ByteBufCodecs.FLOAT.encode(buf, v.move.x)
                ByteBufCodecs.FLOAT.encode(buf, v.move.y)
                ByteBufCodecs.FLOAT.encode(buf, v.yaw)
                ByteBufCodecs.FLOAT.encode(buf, v.pitch)
                ByteBufCodecs.VAR_INT.encode(buf, v.seq)
            }

            override fun decode(buf: ByteBuf): DroneInputPayload {
                val mx = ByteBufCodecs.FLOAT.decode(buf)
                val my = ByteBufCodecs.FLOAT.decode(buf)
                val yaw = ByteBufCodecs.FLOAT.decode(buf)
                val pitch = ByteBufCodecs.FLOAT.decode(buf)
                val seq = ByteBufCodecs.VAR_INT.decode(buf)
                return DroneInputPayload(Vec2(mx, my), yaw, pitch, seq)
            }
        }
    }
}

data class DroneStatePayload(
    val droneId: Int,
    val seq: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val vz: Double = 0.0,
    val confirmedImpact: Boolean = false,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DroneStatePayload> {
        return TYPE
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<DroneStatePayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "drone_state")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, DroneStatePayload> = object : StreamCodec<ByteBuf, DroneStatePayload> {
            override fun encode(buf: ByteBuf, v: DroneStatePayload) {
                ByteBufCodecs.VAR_INT.encode(buf, v.droneId)
                ByteBufCodecs.VAR_INT.encode(buf, v.seq)
                ByteBufCodecs.DOUBLE.encode(buf, v.x)
                ByteBufCodecs.DOUBLE.encode(buf, v.y)
                ByteBufCodecs.DOUBLE.encode(buf, v.z)
                ByteBufCodecs.DOUBLE.encode(buf, v.vx)
                ByteBufCodecs.DOUBLE.encode(buf, v.vy)
                ByteBufCodecs.DOUBLE.encode(buf, v.vz)
                ByteBufCodecs.BOOL.encode(buf, v.confirmedImpact)
            }

            override fun decode(buf: ByteBuf): DroneStatePayload {
                val droneId = ByteBufCodecs.VAR_INT.decode(buf)
                val seq = ByteBufCodecs.VAR_INT.decode(buf)
                val x = ByteBufCodecs.DOUBLE.decode(buf)
                val y = ByteBufCodecs.DOUBLE.decode(buf)
                val z = ByteBufCodecs.DOUBLE.decode(buf)
                val vx = if (buf.isReadable(8)) ByteBufCodecs.DOUBLE.decode(buf) else 0.0
                val vy = if (buf.isReadable(8)) ByteBufCodecs.DOUBLE.decode(buf) else 0.0
                val vz = if (buf.isReadable(8)) ByteBufCodecs.DOUBLE.decode(buf) else 0.0
                val confirmedImpact = if (buf.isReadable) ByteBufCodecs.BOOL.decode(buf) else false
                return DroneStatePayload(droneId, seq, x, y, z, vx, vy, vz, confirmedImpact)
            }
        }
    }
}

data class DroneInputEntry(
    val seq: Int,
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
)

class DroneEntity(
    type: EntityType<out DroneEntity>,
    level: Level
) : Entity(type, level), GeoEntity, UavLinkable, UavInputReceiver, UavPilot {


    var move: Vec2 = Vec2(0f, 0f)

    override var ownerId: UUID? = null

    override var controllerId: UUID? = null

    var inputSeq: Int = 0
    var lastAckSeq: Int = 0
    val inputQueue: kotlin.collections.ArrayDeque<DroneInputEntry> = kotlin.collections.ArrayDeque()
    val pendingInputs: kotlin.collections.ArrayDeque<DroneInputEntry> = kotlin.collections.ArrayDeque()
    private val predictionState = DronePhysics.State()
    private val predictionEngine = UavPredictionEngine(com.curbs.armsrace.api.uav.FpvFlightModel, DronePhysics.spec)
    private var predictionInitialized = false
    var bodyVelocity: Vec3 = Vec3.ZERO
    override var predictedCollision: Boolean = false
    override val pilotEntity: Entity
        get() = this
    var visualRoll: Float = 0f
    var visualPitchLean: Float = 0f
    var visualPitchFollow: Float = 0.3f
    var visualUpdateNanos: Long = 0L

    var visualPrevYaw: Float = 0f
    var visualYawInit: Boolean = false

    private var body: DroneBody? = null
    private var detonating = false

    override fun queueInput(input: UavControlInput) {
        inputQueue.addLast(DroneInputEntry(input.seq, input.move, input.yaw, input.pitch))
        if (inputQueue.size > 64) inputQueue.removeFirst()
    }

    fun updateVisualTilt() {
        if (!level().isClientSide) return
        val now = System.nanoTime()
        var dt = if (visualUpdateNanos == 0L) 0.05f else (now - visualUpdateNanos).toFloat() / 1_000_000_000f
        visualUpdateNanos = now
        dt = dt.coerceIn(0.001f, 0.1f)
        val v = deltaMovement
        val yawRad = Math.toRadians(yRot.toDouble())
        val sinY = kotlin.math.sin(yawRad)
        val cosY = kotlin.math.cos(yawRad)
        val localRight = (v.x * cosY + v.z * sinY).toFloat()
        val localFwd = (-v.x * sinY + v.z * cosY).toFloat()
        val hSpeed = kotlin.math.sqrt(v.x * v.x + v.z * v.z).toFloat()
        val vAbs = kotlin.math.abs(v.y).toFloat()
        var yawRate = 0f
        if (!visualYawInit) {
            visualPrevYaw = yRot
            visualYawInit = true
        } else {
            yawRate = Mth.wrapDegrees(yRot - visualPrevYaw) / dt
            visualPrevYaw = yRot
        }
        val velTerm = (localRight * 12f).coerceIn(-20f, 20f)
        val turnTerm = (-yawRate * 0.12f).coerceIn(-25f, 25f)
        var targetRoll = (velTerm + turnTerm).coerceIn(-32f, 32f)
        var targetPitch = (localFwd * 11f - v.y.toFloat() * 3f).coerceIn(-16f, 30f)
        var targetFollow = 0.3f - 0.2f * (vAbs / (vAbs + hSpeed + 1e-6f)).coerceIn(0f, 1f)
        if (onGround() && v.lengthSqr() < 0.0025) {
            targetRoll = 0f
            targetPitch = 0f
            targetFollow = 0f
        }
        val alpha = 1f - kotlin.math.exp(-10f * dt)
        visualRoll += (targetRoll - visualRoll) * alpha
        visualPitchLean += (targetPitch - visualPitchLean) * alpha
        visualPitchFollow += (targetFollow - visualPitchFollow) * alpha
    }

    private fun ensureBody(): DroneBody? {
        val current = body
        if (current != null && current.active) return current
        current?.remove()
        body = null
        val serverLevel = level() as? ServerLevel ?: return null
        val created = DroneBody.create(
            serverLevel,
            Vec3(x, y + DroneBody.HALF_HEIGHT, z),
            bodyVelocity,
        ) ?: return null
        body = created
        return created
    }

    fun reconcile(
        sx: Double,
        sy: Double,
        sz: Double,
        ack: Int,
        svx: Double = 0.0,
        svy: Double = 0.0,
        svz: Double = 0.0,
        confirmedImpact: Boolean = false,
    ) {
        if (!level().isClientSide) return
        if (ack < lastAckSeq) return
        if (confirmedImpact) predictedCollision = true
        setPos(sx, sy, sz)
        bodyVelocity = Vec3(svx, svy, svz)
        predictionState.velocity = bodyVelocity
        predictionInitialized = true
        if (ack > lastAckSeq) lastAckSeq = ack
        while (pendingInputs.isNotEmpty() && pendingInputs.first().seq <= ack) {
            pendingInputs.removeFirst()
        }
        for (input in pendingInputs) {
            setYRot(input.yaw)
            setXRot(input.pitch.coerceIn(-90f, 90f))
            predict(input)
        }
        bodyVelocity = predictionState.velocity
        deltaMovement = bodyVelocity.scale(1.0 / 20.0)
    }

    private fun predict(input: DroneInputEntry) {
        val predicted = UavPredictionState(position(), predictionState.velocity)
        val velocity = predictionEngine.predict(
            predicted,
            UavControlInput(input.seq, input.move, input.yaw, input.pitch),
        )
        predictionState.velocity = predicted.velocity
        var next = velocity
        val tickVelocity = velocity.scale(1.0 / 20.0)
        val steps = 4
        val step = tickVelocity.scale(1.0 / steps)
        val grounded = !level().noCollision(this, boundingBox.move(0.0, -0.03, 0.0))
        if (grounded && next.y < 0.0) {
            next = Vec3(next.x, 0.0, next.z)
        }
        repeat(steps) {
            if (level().noCollision(this, boundingBox.move(step.x, 0.0, 0.0))) {
                setPos(x + step.x, y, z)
            } else {
                next = Vec3(-next.x * COLLISION_RESTITUTION, next.y, next.z)
            }
            if (grounded && step.y <= 0.0) {
                next = Vec3(next.x, 0.0, next.z)
            } else if (level().noCollision(this, boundingBox.move(0.0, step.y, 0.0))) {
                setPos(x, y + step.y, z)
            } else {
                next = Vec3(next.x, -next.y * COLLISION_RESTITUTION, next.z)
            }
            if (level().noCollision(this, boundingBox.move(0.0, 0.0, step.z))) {
                setPos(x, y, z + step.z)
            } else {
                next = Vec3(next.x, next.y, -next.z * COLLISION_RESTITUTION)
            }
        }
        if (next.y <= 0.0 && !level().noCollision(this, boundingBox.move(0.0, -0.02, 0.0))) {
            next = Vec3(next.x, 0.0, next.z)
        }
        predictionState.velocity = next
        bodyVelocity = next
        deltaMovement = next.scale(1.0 / 20.0)
    }

    private fun onImpact() {
        if (detonating || isRemoved) return
        detonating = true
        discard()
        val pos = blockPosition()

        GenericExplosion.trigger(
            level(),
            blockPosition(),
            128.0F,
            12.0F,
            96.0F,
        )
        GenericExplosionParticleEffect.start(
            level() as ServerLevel?,
            pos,
            128.0F,
        )
        level().playSound(
            null,
            pos,
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.BLOCKS,
            4.0f,
            0.8f,
        )
    }

    override fun isPickable(): Boolean = true

    override fun isAttackable(): Boolean = true

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || isRemoved || detonating) return false
        if (amount <= 0f) return false
        onImpact()
        return true
    }

    fun applyRotation(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
        setYRot(yRot + yawDeltaDeg)
        setXRot(Mth.clamp(xRot + pitchDeltaDeg, -90f, 90f))

        yRotO = yRot
        xRotO = xRot
    }

    override fun applyPilotLook(yaw: Float, pitch: Float) {
        setYRot(yaw)
        setXRot(pitch.coerceIn(-90f, 90f))
        yRotO = yRot
        xRotO = xRot
    }

    override fun remove(reason: Entity.RemovalReason) {
        if (!level().isClientSide) {
            body?.remove()
            body = null
            if (reason == Entity.RemovalReason.DISCARDED || reason == Entity.RemovalReason.KILLED) {
                unlinkController()
            }
        }
        super.remove(reason)
    }

    private fun unlinkController() {
        val id = controllerId ?: return
        val server = level().server ?: return
        ControllerItem.clearLink(server, id)
        ownerId?.let { owner ->
            server.playerList.getPlayer(owner)?.persistentData?.remove("Drone")
        }
    }

    /**
     * Geckolib's per-entity animation state store. Created from `this`, which is
     * safe because GeckoLib looks the cache up by entity UUID, not by identity.
     */
    private val animatableCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animatableCache

    /**
     * No .animations.json exists yet, so there are no controllers to register.
     * The interface still requires the method. Animation controllers get added
     * here once the drone flies and its props spin.
     */
    override fun registerControllers(registrar: AnimatableManager.ControllerRegistrar) {
    }

    override fun tick() {
        super.tick()

        if (!level().isClientSide) {
            if (isInWater()) {
                onImpact()
                return
            }

            while (inputQueue.isNotEmpty() && inputQueue.first().seq <= lastAckSeq) {
                inputQueue.removeFirst()
            }
            val next = if (inputQueue.isNotEmpty()) inputQueue.removeFirst() else null
            if (next != null) {
                move = next.move
                setYRot(next.yaw)
                setXRot(next.pitch.coerceIn(-90f, 90f))
                lastAckSeq = next.seq
            }

            val body = ensureBody() ?: return
            val impactSpeed = body.sync()
            val center = body.center()
            setPos(center.x, center.y - DroneBody.HALF_HEIGHT, center.z)
            val owner = ownerId?.let { (level() as ServerLevel).getPlayerByUUID(it) as? ServerPlayer }
            if (impactSpeed >= HARD_IMPACT_SPEED) {
                if (owner != null) {
                    PacketDistributor.sendToPlayer(
                        owner,
                        DroneStatePayload(id, lastAckSeq, x, y, z, confirmedImpact = true),
                    )
                }
                onImpact()
                return
            }
            body.drive(move, yRot, xRot, DronePhysics.spec.speed)
            bodyVelocity = body.velocity()
            deltaMovement = bodyVelocity.scale(1.0 / 20.0)

            if (owner != null) {
                val sv = bodyVelocity
                PacketDistributor.sendToPlayer(owner, DroneStatePayload(id, lastAckSeq, x, y, z, sv.x, sv.y, sv.z))
            }

            if (ownerId == null) {
                discard()
            }
        } else {
            val mc = Minecraft.getInstance()
            if (mc.cameraEntity !== this) return
            if (isInWater()) {
                discard()
                return
            }
            val move = Vec2(
                (if (mc.options.keyRight.isDown) 1f else 0f) -
                        (if (mc.options.keyLeft.isDown) 1f else 0f),
                (if (mc.options.keyUp.isDown) 1f else 0f) -
                        (if (mc.options.keyDown.isDown) 1f else 0f),
            )
            inputSeq++
            val input = DroneInputEntry(inputSeq, move, yRot, xRot)
            pendingInputs.addLast(input)
            if (!predictionInitialized) {
                predictionState.velocity = bodyVelocity
                predictionInitialized = true
            }
            predict(input)
            if (pendingInputs.size > 64) {
                pendingInputs.removeFirst()
            }
            PacketDistributor.sendToServer(
                DroneInputPayload(
                    move,
                    yRot,
                    xRot,
                    inputSeq
                )
            )
        }
    }

    /**
     * Declares the values that get synced to clients, e.g. owner UUID, throttle
     * and battery. Each one is registered with `SynchedEntityData.defineId(...)`
     * and then read/written through `this.entityData`.
     */
    override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {
        if (level().isClientSide && Minecraft.getInstance().cameraEntity === this) {
            return
        }
        super.lerpTo(x, y, z, yRot, xRot, steps)
    }

    override fun lerpMotion(x: Double, y: Double, z: Double) {
        if (level().isClientSide && Minecraft.getInstance().cameraEntity === this) {
            return
        }
        super.lerpMotion(x, y, z)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
    }

    companion object {
        private const val COLLISION_RESTITUTION = 0.15
        private const val HARD_IMPACT_SPEED = 6.0
    }

    /** Writes our custom data to NBT so it survives a world save. */
    override fun addAdditionalSaveData(tag: CompoundTag) {
        val v = bodyVelocity
        tag.putDouble("BodyVX", v.x)
        tag.putDouble("BodyVY", v.y)
        tag.putDouble("BodyVZ", v.z)
        ownerId?.let { tag.putUUID("Owner", it) }
        controllerId?.let { tag.putUUID("Controller", it) }
    }

    /** Reads back what [addAdditionalSaveData] wrote. */
    override fun readAdditionalSaveData(tag: CompoundTag) {
        if (tag.contains("BodyVX")) {
            bodyVelocity = Vec3(tag.getDouble("BodyVX"), tag.getDouble("BodyVY"), tag.getDouble("BodyVZ"))
        }
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner")
        }
        if (tag.hasUUID("Controller")) {
            controllerId = tag.getUUID("Controller")
        }
    }
}
