package com.curbs.armsrace.entity

import com.julian.createwarfare.effects.server.ShakeEffect
import com.julian.createwarfare.effects.server.SoundWaveEffect
import com.julian.createwarfare.effects.server.WaveEffect
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
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.apache.logging.log4j.LogManager
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
                return DroneStatePayload(droneId, seq, x, y, z, vx, vy, vz)
            }
        }
    }
}

data class DroneInputEntry(
    val seq: Int,
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
    val pos: Vec3,
    val vel: Vec3 = Vec3.ZERO,
)

/**
 * The FPV drone entity. Behaviour is still a STUB - it does not move yet.
 *
 * Flight physics, collision and detonation come later; see the doc comments on
 * each method below for where that logic belongs.
 */
class DroneEntity(
    type: EntityType<out DroneEntity>,
    level: Level
) : Entity(type, level), GeoEntity {


    var move: Vec2 = Vec2(0f, 0f)

    var ownerId: UUID? = null

    var inputSeq: Int = 0
    var lastAckSeq: Int = 0
    val pending: kotlin.collections.ArrayDeque<DroneInputEntry> = kotlin.collections.ArrayDeque()
    val inputQueue: kotlin.collections.ArrayDeque<DroneInputEntry> = kotlin.collections.ArrayDeque()
    var simState: DronePhysics.State = DronePhysics.State()
    var visualRoll: Float = 0f
    var visualPitchLean: Float = 0f
    var visualPitchFollow: Float = 0.3f
    var visualUpdateNanos: Long = 0L

    var visualPrevYaw: Float = 0f
    var visualYawInit: Boolean = false

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

    companion object {
        private val LOGGER = LogManager.getLogger("armsrace")

        const val SPEED = 1.0

        fun calcMovement(move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double = SPEED): Vec3 {
            val strafe = -move.x.toDouble()
            val forward = move.y.toDouble()
            if (strafe == 0.0 && forward == 0.0) return Vec3.ZERO
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val forwardX = -kotlin.math.sin(yaw) * kotlin.math.cos(pitch)
            val forwardY = -kotlin.math.sin(pitch)
            val forwardZ = kotlin.math.cos(yaw) * kotlin.math.cos(pitch)
            val rightX = kotlin.math.cos(yaw)
            val rightZ = kotlin.math.sin(yaw)
            val x = forwardX * forward + rightX * strafe
            val y = forwardY * forward
            val z = forwardZ * forward + rightZ * strafe
            val movement = Vec3(x, y, z)
            if (movement.lengthSqr() <= 0.0) return Vec3.ZERO
            return movement.normalize().scale(speed)
        }
    }

    fun groundCushion(): Double {
        val pos = blockPosition()
        for (d in 1..4) {
            val below = pos.below(d)
            val state = level().getBlockState(below)
            if (!state.isAir) {
                val gap = y - (below.y + 1.0)
                return (1.0 - (gap / 4.0)).coerceIn(0.0, 1.0)
            }
        }
        return 0.0
    }

    fun stepPhysics(move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double = DronePhysics.speed): Vec3 {
        return DronePhysics.step(simState, move, yawDeg, pitchDeg, speed, groundCushion())
    }

    fun reconcile(sx: Double, sy: Double, sz: Double, ack: Int, svx: Double = 0.0, svy: Double = 0.0, svz: Double = 0.0) {
        var ackPos: Vec3? = null
        for (e in pending) {
            if (e.seq == ack) {
                ackPos = e.pos
                break
            }
        }
        while (pending.isNotEmpty() && pending.first().seq <= ack) {
            pending.removeFirst()
        }
        val serverVel = Vec3(svx, svy, svz)
        if (pending.isEmpty()) {
            val dx = sx - x
            val dy = sy - y
            val dz = sz - z
            if (dx * dx + dy * dy + dz * dz < 0.04) {
                if (serverVel.lengthSqr() > 0.0) simState.velocity = serverVel
                return
            }
            setPos(sx, sy, sz)
            simState.velocity = serverVel
            deltaMovement = serverVel
            return
        }
        val base = ackPos ?: return
        val ex = sx - base.x
        val ey = sy - base.y
        val ez = sz - base.z
        if (ex * ex + ey * ey + ez * ez < 0.04) return
        setPos(sx, sy, sz)
        simState.velocity = serverVel
        for (i in pending.indices) {
            val e = pending[i]
            val v = DronePhysics.step(simState, e.move, e.yaw, e.pitch, DronePhysics.speed, groundCushion())
            if (v.lengthSqr() > 1e-9) {
                deltaMovement = v
                move(MoverType.SELF, v)
            } else {
                deltaMovement = Vec3.ZERO
            }
            pending[i] = DroneInputEntry(e.seq, e.move, e.yaw, e.pitch, position(), simState.velocity)
        }
    }

    private fun onImpact() {
        val pos = blockPosition()

        GenericExplosion.trigger(
            level(),
            blockPosition(),
            128.0F,
            12.0F,
            96.0F,
        )

        WaveEffect.start(
            level() as ServerLevel?,
            blockPosition(),
            2.0F,
            12.0F,
            0xffe3b8,
            1.0F,
            0,
            true,
        )

        ShakeEffect.start(
            level() as ServerLevel?,
            blockPosition(),
            24.0F,
            8.0F,
            20,
            true,
        )

        SoundWaveEffect.start(
            level() as ServerLevel?,
            blockPosition(),
            17.15F,
            128.0F,
            SoundEvents.GENERIC_EXPLODE.value(),
            true,
        )



        discard()
    }

    override fun isPickable(): Boolean = true

    override fun isAttackable(): Boolean = true

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || isRemoved) return false
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

    /**
     * Runs 20 times a second while the entity exists. This is where the flight
     * model (gravity + throttle thrust + drag) and the block/entity collision
     * checks that trigger detonation will go.
     */
    override fun tick() {;
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

            var velocity = stepPhysics(move, yRot, xRot)
            val impactSpeed = velocity.length()
            if (onGround() && velocity.y < 0.0 && velocity.y > -0.55) {
                velocity = Vec3(velocity.x, 0.0, velocity.z)
                simState.velocity = Vec3(simState.velocity.x, 0.0, simState.velocity.z)
            }
            deltaMovement = velocity
            if (velocity.lengthSqr() > 1e-9) {
                move(MoverType.SELF, velocity)
                if (horizontalCollision || verticalCollision) {
                    if (impactSpeed > 0.65 || fallDistance > 2.5) {
                        onImpact()
                    } else if (onGround()) {
                        simState.velocity = Vec3(simState.velocity.x * 0.5, 0.0, simState.velocity.z * 0.5)
                    }
                }
            } else {
                move(MoverType.SELF, Vec3.ZERO)
            }

            val owner = ownerId?.let { (level() as ServerLevel).getPlayerByUUID(it) as? ServerPlayer }
            if (owner != null) {
                val sv = simState.velocity
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
            var velocity = stepPhysics(move, yRot, xRot)
            val impactSpeed = velocity.length()
            if (onGround() && velocity.y < 0.0 && velocity.y > -0.55) {
                velocity = Vec3(velocity.x, 0.0, velocity.z)
                simState.velocity = Vec3(simState.velocity.x, 0.0, simState.velocity.z)
            }
            if (velocity.lengthSqr() > 1e-9) {
                deltaMovement = velocity
                move(MoverType.SELF, velocity)
            } else {
                deltaMovement = Vec3.ZERO
            }
            pending.addLast(DroneInputEntry(inputSeq, move, yRot, xRot, position(), simState.velocity))
            if (pending.size > 40) {
                pending.removeFirst()
            }
            PacketDistributor.sendToServer(
                DroneInputPayload(
                    move,
                    yRot,
                    xRot,
                    inputSeq
                )
            )
            if (horizontalCollision || verticalCollision) {
                if (impactSpeed > 0.65 || fallDistance > 2.5) {
                    discard()
                }
            }
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

    /** Writes our custom data to NBT so it survives a world save. */
    override fun addAdditionalSaveData(tag: CompoundTag) {
        val v = simState.velocity
        tag.putDouble("PhysVX", v.x)
        tag.putDouble("PhysVY", v.y)
        tag.putDouble("PhysVZ", v.z)
    }

    /** Reads back what [addAdditionalSaveData] wrote. */
    override fun readAdditionalSaveData(tag: CompoundTag) {
        if (tag.contains("PhysVX")) {
            simState.velocity = Vec3(tag.getDouble("PhysVX"), tag.getDouble("PhysVY"), tag.getDouble("PhysVZ"))
        }
    }
}
