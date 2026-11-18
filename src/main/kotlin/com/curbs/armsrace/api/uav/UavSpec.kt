package com.curbs.armsrace.api.uav

import net.minecraft.resources.ResourceLocation

data class UavFlightSpec(
    val speed: Double = 1.0,
    val heaviness: Double = 0.5,
    val hoverAssist: Double = 0.0,
    val activeDrive: Double = 0.9,
    val coastingDrive: Double = 0.035,
    val propellerThrust: Double = 0.8,
    val gravityPerTick: Double = 11.0 * 0.05,
    val maxDeltaVelocity: Double = 2.4,
    val hardImpactSpeed: Double = 6.0,
    val cruiseSpeed: Double = 1.0,
    val turnRate: Double = 25.0,
)

data class UavVisuals(
    val model: ResourceLocation,
    val texture: ResourceLocation,
    val animation: ResourceLocation = model,
    val scale: Float = 1.0f,
    val bone: String = "bb_main",
    val tilt: Boolean = true,
) {
    companion object {
        fun default(id: ResourceLocation): UavVisuals = UavVisuals(
            ResourceLocation.fromNamespaceAndPath(id.namespace, "geo/${id.path}.geo.json"),
            ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/entity/${id.path}.png"),
        )
    }
}

data class UavSpec(
    val id: ResourceLocation,
    val displayName: String,
    val flightModel: UavFlightModel,
    val flight: UavFlightSpec = UavFlightSpec(),
    val width: Float = 1.0f,
    val height: Float = 0.5f,
    val depth: Float = 1.0f,
    val mass: Double = 5.0,
    val body: UavBodyFactory = SableUavBody.factory(
        width.toDouble(),
        height.toDouble(),
        depth.toDouble(),
        mass,
    ),
    val payloads: List<UavPayloadFactory> = emptyList(),
    val brain: UavBrainFactory? = null,
    val visuals: UavVisuals = UavVisuals.default(id),
    val destroyInWater: Boolean = true,
    val manualTrigger: Boolean = false,
)
