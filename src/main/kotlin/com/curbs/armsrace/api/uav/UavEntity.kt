package com.curbs.armsrace.api.uav

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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
import java.util.UUID

open class UavEntity(
    type: EntityType<out UavEntity>,
    level: Level,
    val uavSpec: UavSpec,
) : Entity(type, level), GeoEntity, UavLinkable, UavInputReceiver, UavPilot, UavReconcilable, UavBrainContext {

    override var ownerId: UUID? = null
    override var controllerId: UUID? = null
    override val pilotEntity: Entity
        get() = this
    override var predictedCollision: Boolean = false

    override var target: Vec3? = null
    override var home: Vec3? = null

    val inputQueue = ArrayDeque<UavControlInput>()
    var bodyVelocity: Vec3 = Vec3.ZERO
        private set

    private val payloadList = uavSpec.payloads.map { it.create() }.toMutableList()
    val payloads: List<UavPayload>
        get() = payloadList
    private val brain = uavSpec.brain?.create()
    private val pendingInputs = ArrayDeque<UavControlInput>()
    private var body: UavBody? = null
    private var inputSequence = 0
    private var lastAcknowledgedInput = 0

    var visualRoll: Float = 0f
    var visualPitchLean: Float = 0f
    var visualPitchFollow: Float = 0.3f
    private var visualUpdateNanos: Long = 0L
    private var visualPrevYaw: Float = 0f
    private var visualYawInit: Boolean = false

    private val animatableCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    fun hasPilot(): Boolean = ownerId != null

    fun addPayload(payload: UavPayload) {
        payloadList.add(payload)
    }

    fun removePayload(payload: UavPayload): Boolean = payloadList.remove(payload)

    override val level: Level
        get() = level()

    override val position: Vec3
        get() = position()

    override val velocity: Vec3
        get() = bodyVelocity

    override val yaw: Float
        get() = yRot

    override val pitch: Float
        get() = xRot

    override val onGround: Boolean
        get() = onGround()

    override val age: Int
        get() = tickCount

    override fun entities(radius: Double): List<Entity> =
        level().getEntities(this, boundingBox.inflate(radius))

    protected open fun onImpact(speed: Double) {
        if (isRemoved) return
        var destroy = false
        for (payload in payloads) {
            if (payload.onImpact(this, speed)) destroy = true
        }
        if (destroy) discard()
    }

    protected open fun onServerState() {
    }

    protected fun triggerPayloads() {
        if (isRemoved) return
        var destroy = false
        for (payload in payloads) {
            if (payload.onTrigger(this)) destroy = true
        }
        if (destroy) discard()
    }

    private fun ensureBody(): UavBody? {
        val current = body
        if (current != null && current.active) return current
        current?.remove()
        body = uavSpec.body.create(level(), this, Vec3(x, y + uavSpec.height * 0.5, z), bodyVelocity)
        return body
    }

    override fun queueInput(input: UavControlInput) {
        if (input.seq <= lastAcknowledgedInput) return
        inputQueue.addLast(input)
        while (inputQueue.size > 64) inputQueue.removeFirst()
    }

    override fun applyPilotLook(yaw: Float, pitch: Float) {
        setYRot(yaw)
        setXRot(pitch.coerceIn(-90f, 90f))
        yRotO = yRot
        xRotO = xRot
    }

    override fun reconcile(
        position: Vec3,
        velocity: Vec3,
        acknowledgedInput: Int,
        confirmedImpact: Boolean,
    ) {
        if (!level().isClientSide) return
        if (acknowledgedInput < lastAcknowledgedInput) return
        if (confirmedImpact) predictedCollision = true
        setPos(position)
        bodyVelocity = velocity
        lastAcknowledgedInput = acknowledgedInput
        while (pendingInputs.firstOrNull()?.seq?.let { it <= acknowledgedInput } == true) {
            pendingInputs.removeFirst()
        }
        for (input in pendingInputs) {
            setYRot(input.yaw)
            setXRot(input.pitch.coerceIn(-90f, 90f))
            predict(input)
        }
    }

    private fun predict(input: UavControlInput) {
        bodyVelocity = UavKinematics.move(this, bodyVelocity, input, uavSpec.flightModel, uavSpec.flight)
        deltaMovement = bodyVelocity.scale(1.0 / 20.0)
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            clientTick()
        } else {
            serverTick()
        }
    }

    protected open fun clientTick() {
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
        val throttle = if (mc.options.keySprint.isDown) 1f else move.y.coerceIn(0f, 1f)
        val action = if (uavSpec.manualTrigger && mc.options.keyUse.isDown) UavControlInput.TRIGGER else 0
        val input = UavControlInput(++inputSequence, move, yRot, xRot, throttle, action)
        pendingInputs.addLast(input)
        while (pendingInputs.size > 64) pendingInputs.removeFirst()
        predict(input)
        PacketDistributor.sendToServer(
            UavInputPayload(move, yRot, xRot, input.seq, throttle, action)
        )
    }

    protected open fun serverTick() {
        if (isInWater() && uavSpec.destroyInWater) {
            onImpact(Double.MAX_VALUE)
            return
        }

        val pilotInput = inputQueue.removeFirstOrNull()
        if (pilotInput != null) {
            lastAcknowledgedInput = maxOf(lastAcknowledgedInput, pilotInput.seq)
        }
        val input = pilotInput ?: brain?.think(this) ?: UavControlInput.idle(yRot, xRot)

        setYRot(input.yaw)
        setXRot(input.pitch.coerceIn(-90f, 90f))

        val activeBody = ensureBody() ?: return
        val impact = activeBody.sync()
        val center = activeBody.center()
        setPos(center.x, center.y - uavSpec.height * 0.5, center.z)

        if (impact >= uavSpec.flight.hardImpactSpeed) {
            sendState(confirmedImpact = true)
            onImpact(impact)
            return
        }

        activeBody.drive(input, uavSpec.flightModel, uavSpec.flight)
        bodyVelocity = activeBody.velocity()
        deltaMovement = bodyVelocity.scale(1.0 / 20.0)

        for (payload in payloads) {
            payload.tick(this)
        }
        if (input.action and UavControlInput.TRIGGER != 0) {
            triggerPayloads()
        }
        onServerState()
        sendState(confirmedImpact = false)
    }

    private fun sendState(confirmedImpact: Boolean) {
        val owner = ownerId?.let { (level() as? ServerLevel)?.getPlayerByUUID(it) as? ServerPlayer } ?: return
        val velocity = bodyVelocity
        PacketDistributor.sendToPlayer(
            owner,
            UavStatePayload(
                id,
                lastAcknowledgedInput,
                x,
                y,
                z,
                velocity.x,
                velocity.y,
                velocity.z,
                confirmedImpact,
            ),
        )
    }

    override fun isPickable(): Boolean = true

    override fun isAttackable(): Boolean = true

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || isRemoved) return false
        if (amount <= 0f) return false
        onImpact(uavSpec.flight.hardImpactSpeed)
        return true
    }

    fun applyRotation(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
        setYRot(yRot + yawDeltaDeg)
        setXRot(Mth.clamp(xRot + pitchDeltaDeg, -90f, 90f))
        yRotO = yRot
        xRotO = xRot
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide) {
            body?.remove()
            body = null
            if (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED) {
                controllerId?.let { controller ->
                    level().server?.let { server -> UavLinks.clear(server, controller) }
                }
                ownerId?.let { owner ->
                    level().server?.playerList?.getPlayer(owner)?.persistentData?.remove("Drone")
                }
            }
        }
        super.remove(reason)
    }

    override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {
        if (level().isClientSide && Minecraft.getInstance().cameraEntity === this) return
        super.lerpTo(x, y, z, yRot, xRot, steps)
    }

    override fun lerpMotion(x: Double, y: Double, z: Double) {
        if (level().isClientSide && Minecraft.getInstance().cameraEntity === this) return
        super.lerpMotion(x, y, z)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animatableCache

    override fun registerControllers(registrar: AnimatableManager.ControllerRegistrar) {
    }

    fun updateVisualTilt() {
        if (!level().isClientSide) return
        val now = System.nanoTime()
        var dt = if (visualUpdateNanos == 0L) 0.05f else (now - visualUpdateNanos).toFloat() / 1_000_000_000f
        visualUpdateNanos = now
        dt = dt.coerceIn(0.001f, 0.1f)
        val velocity = deltaMovement
        val yawRad = Math.toRadians(yRot.toDouble())
        val sinYaw = kotlin.math.sin(yawRad)
        val cosYaw = kotlin.math.cos(yawRad)
        val localRight = (velocity.x * cosYaw + velocity.z * sinYaw).toFloat()
        val localForward = (-velocity.x * sinYaw + velocity.z * cosYaw).toFloat()
        val horizontalSpeed = kotlin.math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val verticalSpeed = kotlin.math.abs(velocity.y).toFloat()
        var yawRate = 0f
        if (!visualYawInit) {
            visualPrevYaw = yRot
            visualYawInit = true
        } else {
            yawRate = Mth.wrapDegrees(yRot - visualPrevYaw) / dt
            visualPrevYaw = yRot
        }
        val velocityTerm = (localRight * 12f).coerceIn(-20f, 20f)
        val turnTerm = (-yawRate * 0.12f).coerceIn(-25f, 25f)
        var targetRoll = (velocityTerm + turnTerm).coerceIn(-32f, 32f)
        var targetPitch = (localForward * 11f - velocity.y.toFloat() * 3f).coerceIn(-16f, 30f)
        var targetFollow = 0.3f - 0.2f * (verticalSpeed / (verticalSpeed + horizontalSpeed + 1e-6f)).coerceIn(0f, 1f)
        if (onGround() && velocity.lengthSqr() < 0.0025) {
            targetRoll = 0f
            targetPitch = 0f
            targetFollow = 0f
        }
        val alpha = 1f - kotlin.math.exp(-10f * dt)
        visualRoll += (targetRoll - visualRoll) * alpha
        visualPitchLean += (targetPitch - visualPitchLean) * alpha
        visualPitchFollow += (targetFollow - visualPitchFollow) * alpha
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putDouble("BodyVX", bodyVelocity.x)
        tag.putDouble("BodyVY", bodyVelocity.y)
        tag.putDouble("BodyVZ", bodyVelocity.z)
        ownerId?.let { tag.putUUID("Owner", it) }
        controllerId?.let { tag.putUUID("Controller", it) }
        target?.let {
            tag.putDouble("TargetX", it.x)
            tag.putDouble("TargetY", it.y)
            tag.putDouble("TargetZ", it.z)
        }
        home?.let {
            tag.putDouble("HomeX", it.x)
            tag.putDouble("HomeY", it.y)
            tag.putDouble("HomeZ", it.z)
        }
        val payloadTag = CompoundTag()
        payloads.forEachIndexed { index, payload ->
            val entry = CompoundTag()
            payload.save(entry)
            payloadTag.put("Payload$index", entry)
        }
        tag.put("Payloads", payloadTag)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        if (tag.contains("BodyVX")) {
            bodyVelocity = Vec3(
                tag.getDouble("BodyVX"),
                tag.getDouble("BodyVY"),
                tag.getDouble("BodyVZ"),
            )
        }
        ownerId = if (tag.hasUUID("Owner")) tag.getUUID("Owner") else null
        controllerId = if (tag.hasUUID("Controller")) tag.getUUID("Controller") else null
        target = if (tag.contains("TargetX")) {
            Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"))
        } else {
            null
        }
        home = if (tag.contains("HomeX")) {
            Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"))
        } else {
            null
        }
        val payloadTag = tag.getCompound("Payloads")
        payloads.forEachIndexed { index, payload ->
            if (payloadTag.contains("Payload$index")) {
                payload.load(payloadTag.getCompound("Payload$index"))
            }
        }
    }

}
