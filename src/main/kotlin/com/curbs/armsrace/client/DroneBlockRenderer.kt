package com.curbs.armsrace.client

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.block.DroneBlockEntity
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer

class DroneBlockRenderer(context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<DroneBlockEntity>(DroneBlockGeoModel()) {

    init {
        withScale(1.4f)
    }
}

class DroneBlockGeoModel : GeoModel<DroneBlockEntity>() {

    override fun getModelResource(animatable: DroneBlockEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")

    override fun getTextureResource(animatable: DroneBlockEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "textures/entity/fpv_drone.png")

    override fun getAnimationResource(animatable: DroneBlockEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")
}
