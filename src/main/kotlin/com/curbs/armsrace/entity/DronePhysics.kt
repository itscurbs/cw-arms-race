package com.curbs.armsrace.entity

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DronePhysics {
    var hoverAssist: Double = 0.0
    var heaviness: Double = 0.5
    var speed: Double = 1.0

    class State {
        var velocity: Vec3 = Vec3.ZERO
    }

    fun step(
        state: State,
        move: Vec2,
        yawDeg: Float,
        pitchDeg: Float,
        speed: Double = DronePhysics.speed,
        groundCushion: Double = 0.0,
    ): Vec3 {
        val h = heaviness.coerceIn(0.0, 1.0)
        val hover = hoverAssist.coerceIn(0.0, 1.0)
        val dead = 0.07 + h * 0.03
        val rawStrafe = -move.x.toDouble()
        val rawForward = move.y.toDouble()
        val mag = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
        var target = Vec3.ZERO
        var thrust = 0.0
        if (mag >= dead) {
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
            val top = (2.9 + (1.55 - 2.9) * h + diving * 0.9 * h) * speed * 12.0
            target = Vec3(
                (-sinY * cosP * forward + cosY * strafe) * top,
                (-sinP * forward) * top,
                (cosY * cosP * forward + sinY * strafe) * top,
            )
            thrust = k * 0.8
        }

        val horizontalDrive = if (mag >= dead) 0.9 else 0.035 + hover * 0.05
        val verticalDrive = if (mag >= dead) 0.9 else 0.0
        var dx = (target.x - state.velocity.x) * horizontalDrive
        var dy = (target.y - state.velocity.y) * verticalDrive
        var dz = (target.z - state.velocity.z) * horizontalDrive
        val deltaLength = sqrt(dx * dx + dy * dy + dz * dz)
        if (deltaLength > 2.4) {
            val scale = 2.4 / deltaLength
            dx *= scale
            dy *= scale
            dz *= scale
        }

        val next = state.velocity.add(dx, dy + thrust - 11.0 * 0.05, dz)
        state.velocity = next
        return next
    }
}
