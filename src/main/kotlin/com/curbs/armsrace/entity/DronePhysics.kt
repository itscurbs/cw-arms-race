package com.curbs.armsrace.entity

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class DronePhysics {
    LEGACY {
        override fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double, groundCushion: Double): Vec3 {
            val strafe = -move.x.toDouble()
            val forward = move.y.toDouble()
            if (strafe == 0.0 && forward == 0.0) {
                state.velocity = Vec3.ZERO
                return Vec3.ZERO
            }
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val forwardX = -sin(yaw) * cos(pitch)
            val forwardY = -sin(pitch)
            val forwardZ = cos(yaw) * cos(pitch)
            val rightX = cos(yaw)
            val rightZ = sin(yaw)
            val x = forwardX * forward + rightX * strafe
            val y = forwardY * forward
            val z = forwardZ * forward + rightZ * strafe
            val movement = Vec3(x, y, z)
            if (movement.lengthSqr() <= 0.0) {
                state.velocity = Vec3.ZERO
                return Vec3.ZERO
            }
            val out = movement.normalize().scale(speed)
            state.velocity = out
            return out
        }
    },
    REALISTIC_ACRO {
        override fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double, groundCushion: Double): Vec3 {
            val dead = 0.07
            val rawStrafe = -move.x.toDouble()
            val rawForward = move.y.toDouble()
            val fwd = if (kotlin.math.abs(rawForward) < dead) 0.0 else rawForward * (kotlin.math.abs(rawForward).coerceAtMost(1.0) * 0.35 + 0.65)
            val str = if (kotlin.math.abs(rawStrafe) < dead) 0.0 else rawStrafe * (kotlin.math.abs(rawStrafe).coerceAtMost(1.0) * 0.35 + 0.65)
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val cosP = cos(pitch)
            val sinP = sin(pitch)
            val sinY = sin(yaw)
            val cosY = cos(yaw)
            var v = state.velocity
            val thrustAccel = 0.62 * speed
            val strafeAccel = 0.46 * speed
            v = v.add(-sinY * cosP * fwd * thrustAccel, -sinP * fwd * thrustAccel, cosY * cosP * fwd * thrustAccel)
            v = v.add(cosY * str * strafeAccel, 0.0, sinY * str * strafeAccel)
            val tilt = kotlin.math.abs(cosP)
            val rotorIdle = 0.115 * tilt * speed
            var gravity = -0.185 + rotorIdle
            if (v.y < 0.0 && groundCushion > 0.0) gravity += 0.16 * groundCushion
            val hSpeed = sqrt(v.x * v.x + v.z * v.z)
            if (hSpeed > 0.9) gravity += (0.05 * ((hSpeed - 0.9).coerceAtMost(1.6) / 1.6)) * tilt
            v = v.add(0.0, gravity, 0.0)
            val sp = v.length()
            val linDrag = 0.955
            val quadDrag = 1.0 / (1.0 + 0.055 * sp)
            v = v.scale(linDrag * quadDrag)
            val maxSpeed = 2.9 * speed.coerceIn(0.4, 2.0)
            if (v.lengthSqr() > maxSpeed * maxSpeed) v = v.normalize().scale(maxSpeed)
            if (v.lengthSqr() < 1e-6) v = Vec3.ZERO
            state.velocity = v
            return v
        }
    },
    STABILIZED_CINE {
        override fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double, groundCushion: Double): Vec3 {
            val dead = 0.09
            val rawStrafe = -move.x.toDouble()
            val rawForward = move.y.toDouble()
            val mag = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val sinY = sin(yaw)
            val cosY = cos(yaw)
            var v = state.velocity
            if (mag < dead) {
                v = Vec3(v.x * 0.76, v.y * 0.58, v.z * 0.76)
                v = v.add(0.0, 0.02 * (0.0 - v.y).coerceIn(-0.06, 0.06), 0.0)
                if (v.lengthSqr() < 1e-6) v = Vec3.ZERO
                state.velocity = v
                return v
            }
            val curved = (mag * mag).coerceAtMost(1.0)
            val k = curved / mag
            val str = rawStrafe * k
            val fwd = rawForward * k
            val topH = 1.05 * speed
            val desX = (-sinY * fwd + cosY * str) * topH
            val desZ = (cosY * fwd + sinY * str) * topH
            val desY = -sin(pitch) * fwd * 0.72 * speed + 0.03 * groundCushion
            val hRate = 0.14
            val vRate = 0.22
            v = Vec3(
                v.x + (desX - v.x) * hRate,
                v.y + (desY - v.y) * vRate - 0.012,
                v.z + (desZ - v.z) * hRate
            )
            v = v.scale(1.0 / (1.0 + 0.012 * v.length()))
            val maxSpeed = 1.35 * speed.coerceIn(0.4, 2.0)
            if (v.lengthSqr() > maxSpeed * maxSpeed) v = v.normalize().scale(maxSpeed)
            state.velocity = v
            return v
        }
    },
    HEAVY_STRIKE {
        override fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double, groundCushion: Double): Vec3 {
            val dead = 0.1
            val rawStrafe = -move.x.toDouble()
            val rawForward = move.y.toDouble()
            val mag = sqrt(rawStrafe * rawStrafe + rawForward * rawForward)
            val yaw = Math.toRadians(yawDeg.toDouble())
            val pitch = Math.toRadians(pitchDeg.toDouble())
            val sinY = sin(yaw)
            val cosY = cos(yaw)
            val cosP = cos(pitch)
            val sinP = sin(pitch)
            var v = state.velocity
            val hSpeed = sqrt(v.x * v.x + v.z * v.z)
            var lift = 0.145 * speed
            if (hSpeed > 0.85) lift += 0.11 * ((hSpeed - 0.85).coerceAtMost(1.4) / 1.4)
            if (v.y < -0.2 && groundCushion > 0.0) lift += 0.12 * groundCushion
            v = v.add(0.0, -0.30 + lift, 0.0)
            if (mag >= dead) {
                val k = (mag * mag).coerceAtMost(1.0) / mag
                val str = rawStrafe * k * 0.55
                val fwd = rawForward * k
                val diving = (-sinP * fwd).coerceAtLeast(0.0)
                val top = (1.55 + diving * 0.9) * speed
                val desX = (-sinY * cosP * fwd + cosY * str) * top
                val desY = (-sinP * fwd) * top
                val desZ = (cosY * cosP * fwd + sinY * str) * top
                val rate = 0.075
                v = Vec3(
                    v.x + (desX - v.x) * rate,
                    v.y + (desY - v.y) * 0.09,
                    v.z + (desZ - v.z) * rate
                )
            } else {
                v = Vec3(v.x * 0.972, v.y, v.z * 0.972)
            }
            v = v.scale(1.0 / (1.0 + 0.018 * v.length()))
            val maxSpeed = 2.3 * speed.coerceIn(0.4, 2.0)
            if (v.lengthSqr() > maxSpeed * maxSpeed) v = v.normalize().scale(maxSpeed)
            if (v.lengthSqr() < 1e-6) v = Vec3.ZERO
            state.velocity = v
            return v
        }
    };

    abstract fun step(state: State, move: Vec2, yawDeg: Float, pitchDeg: Float, speed: Double, groundCushion: Double = 0.0): Vec3

    class State {
        var velocity: Vec3 = Vec3.ZERO
    }

    companion object {
        var active: DronePhysics = HEAVY_STRIKE
    }
}
