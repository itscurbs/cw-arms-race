package com.curbs.armsrace.client

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.item.DroneBlockItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class DroneItemGeoModel : GeoModel<DroneBlockItem>() {
    override fun getModelResource(animatable: DroneBlockItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")

    override fun getTextureResource(animatable: DroneBlockItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "textures/entity/fpv_drone.png")

    override fun getAnimationResource(animatable: DroneBlockItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")
}
