package com.curbs.armsrace.client

import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.DroneGeoModel
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.util.Mth
import software.bernie.geckolib.renderer.GeoEntityRenderer

class DroneRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<DroneEntity>(context, DroneGeoModel()) {

    override fun applyRotations(
        animatable: DroneEntity,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float,
        nativeScale: Float
    ) {
        super.applyRotations(
            animatable,
            poseStack,
            ageInTicks,
            Mth.rotLerp(partialTick, animatable.yRotO, animatable.yRot),
            partialTick,
            nativeScale
        )
    }
}
