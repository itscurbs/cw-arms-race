package com.curbs.armsrace.entity

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.physics.`object`.box.BoxPhysicsObject
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DroneBody private constructor(
    private val level: ServerLevel,
    private val box: BoxPhysicsObject,
    private val handle: RigidBodyHandle,
) {
    private val pose = Pose3d()
    private val linear = Vector3d()
    private val angular = Vector3d()
    private val linearDelta = Vector3d()
    private val angularDelta = Vector3d()
    private val commandedVelocity = Vector3d()
    private var hasCommandedVelocity = false

    val active: Boolean
        get() = box.isActive && handle.isValid()

    fun sync(): Double {
        if (!box.isActive) return 0.0
        handle.getLinearVelocity(linear)
        box.updatePose()
        pose.set(box.getPose())
        handle.getLinearVelocity(linear)
        val verticalImpact = if (
            hasCommandedVelocity &&
            commandedVelocity.y < -MIN_LANDING_SPEED &&
            linear.y > commandedVelocity.y * 0.35
        ) {
            -commandedVelocity.y
        } else {
            0.0
        }
        val beforeHorizontal = sqrt(commandedVelocity.x * commandedVelocity.x + commandedVelocity.z * commandedVelocity.z)
        val afterHorizontal = sqrt(linear.x * linear.x + linear.z * linear.z)
        val horizontalDirectionChanged = commandedVelocity.x * linear.x + commandedVelocity.z * linear.z < 0.0
        val horizontalImpact = if (
            beforeHorizontal > HARD_HORIZONTAL_IMPACT &&
            horizontalDirectionChanged
        ) {
            if (horizontalDirectionChanged) beforeHorizontal + afterHorizontal else beforeHorizontal - afterHorizontal
        } else {
            0.0
        }
        return maxOf(verticalImpact, horizontalImpact)
    }

    fun center(): Vec3 = Vec3(pose.position().x, pose.position().y, pose.position().z)

    fun velocity(): Vec3 {
        if (!active) return Vec3.ZERO
        handle.getLinearVelocity(linear)
        return Vec3(linear.x, linear.y, linear.z)
    }

    fun drive(move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double) {
        if (!active) return
        handle.getLinearVelocity(linear)

        val h = DronePhysics.heaviness.coerceIn(0.0, 1.0)
        val hover = DronePhysics.hoverAssist.coerceIn(0.0, 1.0)
        val deadzone = 0.07 + h * 0.03
        val rawStrafe = -move.x.toDouble()
        val rawForward = move.y.toDouble()
        val mag = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
        val steering = mag >= deadzone

        var targetX = 0.0
        var targetY = 0.0
        var targetZ = 0.0
        var thrust = 0.0
        if (steering) {
            val k = if (mag > 1.0) 1.0 / mag else mag
            val strafe = rawStrafe * k * (1.0 - h * 0.45)
            val forward = rawForward * k
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val sinY = sin(yaw)
            val cosY = cos(yaw)
            val cosP = cos(pitch)
            val sinP = sin(pitch)
            val diving = (-sinP * forward).coerceAtLeast(0.0)
            val topBlocks = (2.9 + (1.55 - 2.9) * h + diving * 0.9 * h) * speed
            val top = topBlocks * BLOCKS_PER_TICK
            targetX = (-sinY * cosP * forward + cosY * strafe) * top
            targetY = (-sinP * forward) * top
            targetZ = (cosY * cosP * forward + sinY * strafe) * top
            thrust = k * PROPELLER_THRUST
        }

        val horizontalDrive = if (steering) ACTIVE_DRIVE else COASTING_DRIVE + hover * 0.05
        val verticalDrive = if (steering) ACTIVE_DRIVE else 0.0

        var deltaX = (targetX - linear.x) * horizontalDrive
        var deltaY = (targetY - linear.y) * verticalDrive
        var deltaZ = (targetZ - linear.z) * horizontalDrive

        val deltaLength = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        if (deltaLength > MAX_DELTA_VELOCITY) {
            val scale = MAX_DELTA_VELOCITY / deltaLength
            deltaX *= scale
            deltaY *= scale
            deltaZ *= scale
        }

        linearDelta.set(deltaX, deltaY + thrust, deltaZ)

        handle.getAngularVelocity(angular)
        angularDelta.set(
            -angular.x * ANGULAR_DAMPING,
            -angular.y * ANGULAR_DAMPING,
            -angular.z * ANGULAR_DAMPING,
        )
        handle.addLinearAndAngularVelocity(linearDelta, angularDelta)
        handle.getLinearVelocity(commandedVelocity)
        hasCommandedVelocity = true
    }

    fun setVelocity(velocity: Vec3) {
        if (!active) return
        handle.getLinearVelocity(linear)
        linear.set(velocity.x - linear.x, velocity.y - linear.y, velocity.z - linear.z)
        angular.zero()
        handle.addLinearAndAngularVelocity(linear, angular)
    }

    fun remove() {
        SubLevelPhysicsSystem.get(level)?.removeObject(box)
    }

    companion object {
        const val HALF_WIDTH = 0.5
        const val HALF_HEIGHT = 0.25
        const val HALF_LENGTH = 0.5
        const val MASS = 5.0

        private const val BLOCKS_PER_TICK = 12.0
        private const val ACTIVE_DRIVE = 0.9
        private const val COASTING_DRIVE = 0.035
        private const val ANGULAR_DAMPING = 0.5
        private const val MAX_DELTA_VELOCITY = 2.4
        private const val PROPELLER_THRUST = 0.8
        private const val MIN_LANDING_SPEED = 1.5
        private const val HARD_HORIZONTAL_IMPACT = 7.0

        fun create(level: ServerLevel, center: Vec3, velocity: Vec3): DroneBody? {
            val system = SubLevelPhysicsSystem.get(level) ?: return null
            val pose = Pose3d()
            pose.position().set(center.x, center.y, center.z)
            val box = BoxPhysicsObject(
                pose,
                Vector3d(HALF_WIDTH, HALF_HEIGHT, HALF_LENGTH),
                MASS,
            )
            system.addObject(box)
            val handle = RigidBodyHandle.of(level, box) ?: run {
                system.removeObject(box)
                return null
            }
            return DroneBody(level, box, handle).also { it.setVelocity(velocity) }
        }
    }
}
