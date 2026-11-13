package com.curbs.armsrace.api.uav

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.physics.`object`.box.BoxPhysicsObject
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import org.joml.Vector3d
import kotlin.math.sqrt
import java.util.UUID

data class UavFlightSpec(
    val speed: Double = 1.0,
    val heaviness: Double = 0.5,
    val hoverAssist: Double = 0.0,
    val activeDrive: Double = 0.9,
    val coastingDrive: Double = 0.035,
    val propellerThrust: Double = 0.8,
    val gravityPerTick: Double = 11.0 * 0.05,
    val maxDeltaVelocity: Double = 2.4,
)

data class UavSpec(
    val id: ResourceLocation,
    val flight: UavFlightSpec,
    val flightModel: UavFlightModel,
    val width: Float = 1.0f,
    val height: Float = 0.5f,
    val body: UavBodyFactory,
    val payloads: List<UavPayloadFactory> = emptyList(),
)

fun interface UavBodyFactory {
    fun create(level: Level, position: Vec3, velocity: Vec3): UavBody?
}

interface UavBody {
    val active: Boolean
    fun center(): Vec3
    fun velocity(): Vec3
    fun sync(): Double
    fun drive(input: UavControlInput, model: UavFlightModel, spec: UavFlightSpec)
    fun setVelocity(velocity: Vec3)
    fun remove()
}

interface UavLinkable {
    var ownerId: UUID?
    var controllerId: UUID?
}

fun interface UavInputReceiver {
    fun queueInput(input: UavControlInput)
}

interface UavPilot {
    val pilotEntity: Entity
    var predictedCollision: Boolean
    fun applyPilotLook(yaw: Float, pitch: Float)
}

class SableUavBody private constructor(
    private val level: ServerLevel,
    private val box: BoxPhysicsObject,
    private val handle: RigidBodyHandle,
) : UavBody {
    private val pose = Pose3d()
    private val linear = Vector3d()
    private val angular = Vector3d()
    private val linearDelta = Vector3d()
    private val angularDelta = Vector3d()
    private val commandedVelocity = Vector3d()
    private var hasCommandedVelocity = false

    override val active: Boolean
        get() = box.isActive && handle.isValid()

    override fun sync(): Double {
        if (!box.isActive) return 0.0
        box.updatePose()
        pose.set(box.getPose())
        handle.getLinearVelocity(linear)
        val verticalImpact = if (
            hasCommandedVelocity &&
            commandedVelocity.y < -1.5 &&
            linear.y > commandedVelocity.y * 0.35
        ) {
            -commandedVelocity.y
        } else {
            0.0
        }
        val beforeHorizontal = sqrt(commandedVelocity.x * commandedVelocity.x + commandedVelocity.z * commandedVelocity.z)
        val afterHorizontal = sqrt(linear.x * linear.x + linear.z * linear.z)
        val reversed = commandedVelocity.x * linear.x + commandedVelocity.z * linear.z < 0.0
        val horizontalImpact = if (beforeHorizontal > 7.0 && reversed) {
            beforeHorizontal + afterHorizontal
        } else {
            0.0
        }
        return maxOf(verticalImpact, horizontalImpact)
    }

    override fun center(): Vec3 = Vec3(pose.position().x, pose.position().y, pose.position().z)

    override fun velocity(): Vec3 {
        if (!active) return Vec3.ZERO
        handle.getLinearVelocity(linear)
        return Vec3(linear.x, linear.y, linear.z)
    }

    override fun drive(input: UavControlInput, model: UavFlightModel, spec: UavFlightSpec) {
        if (!active) return
        handle.getLinearVelocity(linear)
        val state = UavFlightState(Vec3(linear.x, linear.y, linear.z))
        val desired = model.step(state, input, spec)
        var dx = desired.x - linear.x
        var dy = desired.y - linear.y + spec.gravityPerTick
        var dz = desired.z - linear.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length > spec.maxDeltaVelocity) {
            val scale = spec.maxDeltaVelocity / length
            dx *= scale
            dy *= scale
            dz *= scale
        }
        linearDelta.set(dx, dy, dz)
        handle.getAngularVelocity(angular)
        angularDelta.set(-angular.x * 0.5, -angular.y * 0.5, -angular.z * 0.5)
        handle.addLinearAndAngularVelocity(linearDelta, angularDelta)
        handle.getLinearVelocity(commandedVelocity)
        hasCommandedVelocity = true
    }

    override fun setVelocity(velocity: Vec3) {
        if (!active) return
        handle.getLinearVelocity(linear)
        linear.set(velocity.x - linear.x, velocity.y - linear.y, velocity.z - linear.z)
        angular.zero()
        handle.addLinearAndAngularVelocity(linear, angular)
    }

    override fun remove() {
        SubLevelPhysicsSystem.get(level)?.removeObject(box)
    }

    companion object {
        fun factory(
            width: Double,
            height: Double,
            depth: Double,
            mass: Double,
        ): UavBodyFactory = UavBodyFactory { level, position, velocity ->
            val serverLevel = level as? ServerLevel ?: return@UavBodyFactory null
            val system = SubLevelPhysicsSystem.get(serverLevel) ?: return@UavBodyFactory null
            val pose = Pose3d()
            pose.position().set(position.x, position.y, position.z)
            val box = BoxPhysicsObject(
                pose,
                Vector3d(width * 0.5, height * 0.5, depth * 0.5),
                mass,
            )
            system.addObject(box)
            val handle = RigidBodyHandle.of(serverLevel, box) ?: run {
                system.removeObject(box)
                return@UavBodyFactory null
            }
            SableUavBody(serverLevel, box, handle).also { it.setVelocity(velocity) }
        }
    }
}

fun interface UavPayloadFactory {
    fun create(): UavPayload
}

interface UavPayload {
    fun tick()
    fun onImpact(speed: Double): Boolean
    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag)
}

data class UavControlInput(
    val seq: Int,
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
)

data class UavFlightState(
    var velocity: Vec3 = Vec3.ZERO,
)

fun interface UavFlightModel {
    fun step(state: UavFlightState, input: UavControlInput, spec: UavFlightSpec): Vec3
}

object FpvFlightModel : UavFlightModel {
    override fun step(state: UavFlightState, input: UavControlInput, spec: UavFlightSpec): Vec3 {
        val heaviness = spec.heaviness.coerceIn(0.0, 1.0)
        val hover = spec.hoverAssist.coerceIn(0.0, 1.0)
        val deadzone = 0.07 + heaviness * 0.03
        val rawStrafe = -input.move.x.toDouble()
        val rawForward = input.move.y.toDouble()
        val magnitude = kotlin.math.sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
        var target = Vec3.ZERO
        var thrust = 0.0
        if (magnitude >= deadzone) {
            val scale = if (magnitude > 1.0) 1.0 / magnitude else magnitude
            val strafe = rawStrafe * scale * (1.0 - heaviness * 0.45)
            val forward = rawForward * scale
            val yaw = Math.toRadians(input.yaw.toDouble())
            val pitch = Math.toRadians(input.pitch.toDouble())
            val sinYaw = kotlin.math.sin(yaw)
            val cosYaw = kotlin.math.cos(yaw)
            val cosPitch = kotlin.math.cos(pitch)
            val sinPitch = kotlin.math.sin(pitch)
            val diving = (-sinPitch * forward).coerceAtLeast(0.0)
            val top = (2.9 + (1.55 - 2.9) * heaviness + diving * 0.9 * heaviness) * spec.speed * 12.0
            target = Vec3(
                (-sinYaw * cosPitch * forward + cosYaw * strafe) * top,
                (-sinPitch * forward) * top,
                (cosYaw * cosPitch * forward + sinYaw * strafe) * top,
            )
            thrust = scale * spec.propellerThrust
        }

        val horizontalDrive = if (magnitude >= deadzone) spec.activeDrive else spec.coastingDrive + hover * 0.05
        val verticalDrive = if (magnitude >= deadzone) spec.activeDrive else 0.0
        var dx = (target.x - state.velocity.x) * horizontalDrive
        var dy = (target.y - state.velocity.y) * verticalDrive
        var dz = (target.z - state.velocity.z) * horizontalDrive
        val deltaLength = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (deltaLength > spec.maxDeltaVelocity) {
            val scale = spec.maxDeltaVelocity / deltaLength
            dx *= scale
            dy *= scale
            dz *= scale
        }

        state.velocity = state.velocity.add(dx, dy + thrust - spec.gravityPerTick, dz)
        return state.velocity
    }
}

data class UavPredictionState(
    var position: Vec3,
    var velocity: Vec3 = Vec3.ZERO,
)

class UavPredictionEngine(
    private val model: UavFlightModel,
    private val spec: UavFlightSpec,
    private val tickRate: Double = 20.0,
) {
    fun predict(state: UavPredictionState, input: UavControlInput): Vec3 {
        val flightState = UavFlightState(state.velocity)
        val nextVelocity = model.step(flightState, input, spec)
        state.velocity = nextVelocity
        state.position = state.position.add(nextVelocity.scale(1.0 / tickRate))
        return nextVelocity
    }

    fun reconcile(
        state: UavPredictionState,
        position: Vec3,
        velocity: Vec3,
        pendingInputs: Iterable<UavControlInput>,
    ) {
        state.position = position
        state.velocity = velocity
        for (input in pendingInputs) {
            predict(state, input)
        }
    }
}
