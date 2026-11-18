package com.curbs.armsrace.uav

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.api.uav.ExplosiveWarhead
import com.curbs.armsrace.api.uav.FpvFlightModel
import com.curbs.armsrace.api.uav.UavExplosionSpec
import com.curbs.armsrace.api.uav.UavFlightSpec
import com.curbs.armsrace.api.uav.UavPayloadFactory
import com.curbs.armsrace.api.uav.UavRegistration
import com.curbs.armsrace.api.uav.UavSpec
import com.curbs.armsrace.api.uav.UavVisuals
import net.minecraft.resources.ResourceLocation

object FpvDrone {
    private fun resource(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, path)

    val TYPE = UavRegistration.register(
        "fpv_drone",
        UavSpec(
            id = resource("fpv_drone"),
            displayName = "FPV Drone",
            flight = UavFlightSpec(
                speed = 1.0,
                heaviness = 0.5,
                hoverAssist = 0.0,
                activeDrive = 0.9,
                coastingDrive = 0.035,
                propellerThrust = 0.8,
                gravityPerTick = 11.0 * 0.05,
                maxDeltaVelocity = 2.4,
                hardImpactSpeed = 6.0,
            ),
            flightModel = FpvFlightModel,
            width = 1.0f,
            height = 0.5f,
            depth = 1.0f,
            mass = 5.0,
            payloads = listOf(
                UavPayloadFactory { ExplosiveWarhead(UavExplosionSpec()) },
            ),
            visuals = UavVisuals(
                model = resource("geo/fpv_drone.geo.json"),
                texture = resource("textures/entity/fpv_drone.png"),
                animation = resource("geo/fpv_drone.geo.json"),
                scale = 1.0f,
                bone = "bb_main",
            ),
        ),
    )
}
