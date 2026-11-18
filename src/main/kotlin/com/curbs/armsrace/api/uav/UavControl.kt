package com.curbs.armsrace.api.uav

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

data class UavControlInput(
    val seq: Int,
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
    val throttle: Float = 0f,
    val action: Int = 0,
) {
    companion object {
        const val TRIGGER = 1

        fun idle(yaw: Float, pitch: Float): UavControlInput =
            UavControlInput(0, Vec2.ZERO, yaw, pitch)
    }
}

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
        val magnitude = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
        var target = Vec3.ZERO
        var thrust = 0.0
        if (magnitude >= deadzone) {
            val scale = if (magnitude > 1.0) 1.0 / magnitude else magnitude
            val strafe = rawStrafe * scale * (1.0 - heaviness * 0.45)
            val forward = rawForward * scale
            val yaw = Math.toRadians(input.yaw.toDouble())
            val pitch = Math.toRadians(input.pitch.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)
            val cosPitch = cos(pitch)
            val sinPitch = sin(pitch)
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
        val deltaLength = sqrt(dx * dx + dy * dy + dz * dz)
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

object FixedWingFlightModel : UavFlightModel {
    override fun step(state: UavFlightState, input: UavControlInput, spec: UavFlightSpec): Vec3 {
        val throttle = (if (input.throttle > 0f) input.throttle else input.move.y).coerceIn(0f, 1f)
        val cruise = spec.speed * spec.cruiseSpeed * 12.0
        val airspeed = cruise * (0.35 + 0.65 * throttle)
        val yaw = Math.toRadians((input.yaw + input.move.x * spec.turnRate).toDouble())
        val pitch = Math.toRadians(input.pitch.coerceIn(-45f, 45f).toDouble())
        val cosPitch = cos(pitch)
        val direction = Vec3(
            -sin(yaw) * cosPitch,
            -sin(pitch),
            cos(yaw) * cosPitch,
        )
        val lift = (airspeed / cruise).coerceIn(0.0, 1.2)
        val target = direction.scale(airspeed)
        val drive = if (throttle > 0f) spec.activeDrive else spec.coastingDrive

        var dx = (target.x - state.velocity.x) * drive
        var dy = (target.y - state.velocity.y) * drive
        var dz = (target.z - state.velocity.z) * drive
        val deltaLength = sqrt(dx * dx + dy * dy + dz * dz)
        if (deltaLength > spec.maxDeltaVelocity) {
            val scale = spec.maxDeltaVelocity / deltaLength
            dx *= scale
            dy *= scale
            dz *= scale
        }

        state.velocity = state.velocity.add(dx, dy + (lift * lift - 1.0) * spec.gravityPerTick, dz)
        return state.velocity
    }
}

fun interface UavBrainFactory {
    fun create(): UavBrain
}

interface UavBrainContext {
    val level: Level
    val position: Vec3
    val velocity: Vec3
    val yaw: Float
    val pitch: Float
    val onGround: Boolean
    val age: Int
    var target: Vec3?
    var home: Vec3?

    fun entities(radius: Double): List<Entity>
}

interface UavBrain {
    fun think(context: UavBrainContext): UavControlInput

    fun reset() {}
}

open class WaypointBrain(
    private val arriveDistance: Double = 6.0,
    private val cruiseThrottle: Float = 1.0f,
    private val maxPitch: Float = 35.0f,
) : UavBrain {
    override fun think(context: UavBrainContext): UavControlInput {
        val target = context.target ?: return UavControlInput.idle(context.yaw, 0f)
        val dx = target.x - context.position.x
        val dy = target.y - context.position.y
        val dz = target.z - context.position.z
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal < arriveDistance) {
            return UavControlInput(0, Vec2.ZERO, context.yaw, 0f, throttle = cruiseThrottle)
        }
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-maxPitch, maxPitch)
        return UavControlInput(0, Vec2(0f, 1f), yaw, pitch, throttle = cruiseThrottle)
    }
}

class KamikazeBrain(
    private val maxPitch: Float = 70.0f,
) : UavBrain {
    override fun think(context: UavBrainContext): UavControlInput {
        val target = context.target ?: return UavControlInput.idle(context.yaw, 0f)
        val dx = target.x - context.position.x
        val dy = target.y - context.position.y
        val dz = target.z - context.position.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-maxPitch, maxPitch)
        return UavControlInput(
            0,
            Vec2(0f, 1f),
            yaw,
            pitch,
            throttle = 1f,
            action = UavControlInput.TRIGGER,
        )
    }
}

class BombingRunBrain(
    private val dropDistance: Double = 8.0,
    private val approachAltitude: Double = 20.0,
) : UavBrain {
    private var dropped = false

    override fun think(context: UavBrainContext): UavControlInput {
        val target = context.target ?: return UavControlInput.idle(context.yaw, 0f)
        val dx = target.x - context.position.x
        val dy = target.y - context.position.y
        val dz = target.z - context.position.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val aboveTarget = context.position.y > target.y + approachAltitude * 0.5
        val pitch = if (aboveTarget) 0f else (-Math.toDegrees(atan2(dy, horizontal))).toFloat().coerceIn(-30f, 30f)
        val action = if (!dropped && aboveTarget && horizontal < dropDistance) {
            dropped = true
            UavControlInput.TRIGGER
        } else {
            0
        }
        return UavControlInput(0, Vec2(0f, 1f), yaw, pitch, throttle = 1f, action = action)
    }

    override fun reset() {
        dropped = false
    }
}
