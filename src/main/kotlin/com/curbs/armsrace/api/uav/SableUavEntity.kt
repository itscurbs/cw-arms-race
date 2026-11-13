package com.curbs.armsrace.api.uav

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.util.UUID

open class SableUavEntity(
    type: EntityType<out SableUavEntity>,
    level: Level,
    val uavSpec: UavSpec,
) : Entity(type, level), UavLinkable, UavInputReceiver, UavPilot {
    override var ownerId: UUID? = null
    override var controllerId: UUID? = null
    override val pilotEntity: Entity
        get() = this
    override var predictedCollision: Boolean = false
    val inputQueue = ArrayDeque<UavControlInput>()
    val prediction = UavPredictionEngine(uavSpec.flightModel, uavSpec.flight)
    val predictionState = UavPredictionState(Vec3.ZERO)
    var bodyVelocity: Vec3 = Vec3.ZERO
        private set
    private var body: UavBody? = null
    private var inputSequence = 0
    private var lastAcknowledgedInput = 0
    private val pendingInputs = ArrayDeque<UavControlInput>()
    private val payloads = uavSpec.payloads.map { it.create() }

    protected open fun createInitialInput(): UavControlInput =
        UavControlInput(0, Vec2.ZERO, yRot, xRot)

    protected open fun onImpact(speed: Double) {
        payloads.forEach { if (it.onImpact(speed)) discard() }
    }

    protected open fun onInput(input: UavControlInput) {
    }

    protected open fun onServerState() {
    }

    protected fun ensureBody(): UavBody? {
        val current = body
        if (current != null && current.active) return current
        current?.remove()
        body = uavSpec.body.create(level(), Vec3(x, y + uavSpec.height * 0.5, z), bodyVelocity)
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

    fun reconcile(
        position: Vec3,
        velocity: Vec3,
        acknowledgedInput: Int,
    ) {
        if (!level().isClientSide || acknowledgedInput < lastAcknowledgedInput) return
        setPos(position)
        bodyVelocity = velocity
        prediction.reconcile(
            predictionState,
            position,
            velocity,
            pendingInputs.filter { it.seq > acknowledgedInput },
        )
        lastAcknowledgedInput = acknowledgedInput
        while (pendingInputs.firstOrNull()?.seq?.let { it <= acknowledgedInput } == true) {
            pendingInputs.removeFirst()
        }
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
        val input = createInitialInput().copy(seq = ++inputSequence)
        pendingInputs.addLast(input)
        prediction.predict(predictionState, input)
        setPos(predictionState.position)
        bodyVelocity = predictionState.velocity
        onInput(input)
    }

    protected open fun serverTick() {
        val input = inputQueue.removeFirstOrNull() ?: createInitialInput()
        lastAcknowledgedInput = maxOf(lastAcknowledgedInput, input.seq)
        setYRot(input.yaw)
        setXRot(input.pitch.coerceIn(-90f, 90f))
        val activeBody = ensureBody() ?: return
        val impact = activeBody.sync()
        val center = activeBody.center()
        setPos(center.x, center.y - uavSpec.height * 0.5, center.z)
        if (impact > 0.0) onImpact(impact)
        activeBody.drive(input, uavSpec.flightModel, uavSpec.flight)
        bodyVelocity = activeBody.velocity()
        deltaMovement = bodyVelocity.scale(1.0 / 20.0)
        payloads.forEach { it.tick() }
        onServerState()
    }

    override fun remove(reason: RemovalReason) {
        body?.remove()
        body = null
        super.remove(reason)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putDouble("BodyVX", bodyVelocity.x)
        tag.putDouble("BodyVY", bodyVelocity.y)
        tag.putDouble("BodyVZ", bodyVelocity.z)
        ownerId?.let { tag.putUUID("Owner", it) }
        controllerId?.let { tag.putUUID("Controller", it) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        bodyVelocity = Vec3(
            tag.getDouble("BodyVX"),
            tag.getDouble("BodyVY"),
            tag.getDouble("BodyVZ"),
        )
        ownerId = if (tag.hasUUID("Owner")) tag.getUUID("Owner") else null
        controllerId = if (tag.hasUUID("Controller")) tag.getUUID("Controller") else null
    }
}
