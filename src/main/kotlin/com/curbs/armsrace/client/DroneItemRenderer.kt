package com.curbs.armsrace.client

import com.curbs.armsrace.item.DroneItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import software.bernie.geckolib.renderer.GeoItemRenderer

class DroneItemRenderer : GeoItemRenderer<DroneItem> {
    constructor() : super(DroneItemGeoModel())

    private companion object {
        const val CENTER_X = 0f
        const val CENTER_Y = 0.094f
        const val CENTER_Z = -0.031f
    }

    override fun renderByItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        poseStack.pushPose()

        when (displayContext) {
            ItemDisplayContext.GUI -> {
                poseStack.translate(0f, -1.75f / 16f, 0f)
                poseStack.rotateAround(
                    Axis.YP.rotationDegrees(45f),
                    CENTER_X,
                    CENTER_Y,
                    CENTER_Z
                )
                poseStack.rotateAround(
                    Axis.XP.rotationDegrees(25f),
                    CENTER_X,
                    CENTER_Y,
                    CENTER_Z
                )
            }
            ItemDisplayContext.GROUND -> {
                poseStack.scale(0.9f, 0.9f, 0.9f)
            }
            ItemDisplayContext.FIXED -> {
                poseStack.translate(0f, -0.103f, 0.034f)
                poseStack.scale(1.1f, 1.1f, 1.1f)
                poseStack.rotateAround(Quaternionf().rotationX(Math.toRadians(65.0).toFloat()), CENTER_X, CENTER_Y, CENTER_Z)
            }
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND -> {
                poseStack.scale(0.8f, 0.8f, 0.8f)
                poseStack.translate(0f, 0.2f, 0f)
            }
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> {
                poseStack.scale(0.75f, 0.75f, 0.75f)
                poseStack.translate(0f, 0.2f, 0f)
            }
            ItemDisplayContext.HEAD -> {
                poseStack.scale(0.8f, 0.8f, 0.8f)
                poseStack.translate(0f, 0.15f, 0f)
            }
            else -> {
                poseStack.scale(0.9f, 0.9f, 0.9f)
            }
        }

        super.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)
        poseStack.popPose()
    }
}
