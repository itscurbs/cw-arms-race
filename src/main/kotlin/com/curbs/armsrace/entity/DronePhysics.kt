package com.curbs.armsrace.entity

import com.curbs.armsrace.api.uav.FpvFlightModel
import com.curbs.armsrace.api.uav.UavControlInput
import com.curbs.armsrace.api.uav.UavFlightSpec
import com.curbs.armsrace.api.uav.UavFlightState
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

object DronePhysics {
    val spec = UavFlightSpec()

    class State {
        var velocity: Vec3 = Vec3.ZERO
    }

    fun step(
        state: State,
        move: Vec2,
        yawDeg: Float,
        pitchDeg: Float,
        speed: Double = spec.speed,
        groundCushion: Double = 0.0,
    ): Vec3 {
        val flightState = UavFlightState(state.velocity)
        val result = FpvFlightModel.step(
            flightState,
            UavControlInput(0, move, yawDeg, pitchDeg),
            spec.copy(speed = speed),
        )
        state.velocity = result
        return result
    }
}
