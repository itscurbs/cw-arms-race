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

    fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double = DronePhysics.speed, groundCushion: Double = 0.0): Vec3 {
        val h = heaviness.coerceIn(0.0, 1.0)
        val hover = hoverAssist.coerceIn(0.0, 1.0)
        val dead = 0.07 + h * 0.03
        val rawStrafe = -move.x.toDouble()
        val rawForward = move.y.toDouble()
        val mag = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val sinY = sin(yaw)
        val cosY = cos(yaw)
        val cosP = cos(pitch)
        val sinP = sin(pitch)
        val tilt = kotlin.math.abs(cosP)
        var v = state.velocity
        val gravityBase = 0.185 + (0.30 - 0.185) * h
        val hSpeed = sqrt(v.x * v.x + v.z * v.z)
        var lift = gravityBase * tilt * hover
        if (hSpeed > 0.9) lift += 0.05 * ((hSpeed - 0.9).coerceAtMost(1.6) / 1.6) * tilt
        if (v.y < 0.0 && groundCushion > 0.0) lift += (0.16 + (0.12 - 0.16) * h) * groundCushion
        val gravity = -gravityBase + lift
        if (mag < dead) {
            v = v.add(0.0, gravity, 0.0)
            val hDamp = 0.76 + (1.0 - hover) * 0.15 + h * 0.06
            val vDamp = 0.58 + (1.0 - hover) * 0.37
            v = Vec3(v.x * hDamp, v.y * vDamp, v.z * hDamp)
            if (v.lengthSqr() < 1e-6) v = Vec3.ZERO
            state.velocity = v
            return v
        }
        val k = (mag * mag).coerceAtMost(1.0) / mag
        val strafeAuthority = 1.0 - h * 0.45
        val str = rawStrafe * k * strafeAuthority
        val fwd = rawForward * k
        val diving = (-sinP * fwd).coerceAtLeast(0.0)
        val top = ((2.9 + (1.55 - 2.9) * h) + diving * 0.9 * h) * speed
        val desX = (-sinY * cosP * fwd + cosY * str) * top
        val desY = (-sinP * fwd) * top
        val desZ = (cosY * cosP * fwd + sinY * str) * top
        val hRate = 0.5 + (0.075 - 0.5) * h
        val vRate = 0.5 + (0.09 - 0.5) * h
        v = Vec3(
            v.x + (desX - v.x) * hRate,
            v.y + (desY - v.y) * vRate + gravity,
            v.z + (desZ - v.z) * hRate
        )
        v = v.scale(1.0 / (1.0 + (0.055 + (0.018 - 0.055) * h) * v.length()))
        val maxSpeed = (2.9 + (2.3 - 2.9) * h) * speed.coerceIn(0.4, 2.0)
        if (v.lengthSqr() > maxSpeed * maxSpeed) v = v.normalize().scale(maxSpeed)
        if (v.lengthSqr() < 1e-6) v = Vec3.ZERO
        state.velocity = v
        return v
    }
}
