package com.curbs.armsrace.client

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.item.DroneItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class DroneItemGeoModel : GeoModel<DroneItem>() {
    override fun getModelResource(animatable: DroneItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")

    override fun getTextureResource(animatable: DroneItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "textures/entity/fpv_drone.png")

    override fun getAnimationResource(animatable: DroneItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")
}
