package com.curbs.armsrace.api.uav.client

import com.curbs.armsrace.api.uav.GenericUavStationBlockEntity
import com.curbs.armsrace.api.uav.UavEntity
import com.curbs.armsrace.api.uav.UavType
import com.curbs.armsrace.api.uav.UavVisuals
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import org.joml.Quaternionf
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.SingletonGeoAnimatable
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.function.Consumer

class UavGeoModel : GeoModel<UavEntity>() {
    override fun getModelResource(animatable: UavEntity): ResourceLocation =
        animatable.uavSpec.visuals.model

    override fun getTextureResource(animatable: UavEntity): ResourceLocation =
        animatable.uavSpec.visuals.texture

    override fun getAnimationResource(animatable: UavEntity): ResourceLocation =
        animatable.uavSpec.visuals.animation

    override fun setCustomAnimations(
        animatable: UavEntity,
        instanceId: Long,
        animationState: AnimationState<UavEntity>,
    ) {
        val visuals = animatable.uavSpec.visuals
        if (!visuals.tilt) return
        getAnimationProcessor().getBone(visuals.bone)?.let { bone ->
            animatable.updateVisualTilt()
            bone.rotX = -(animatable.xRot * animatable.visualPitchFollow + animatable.visualPitchLean) * Math.PI.toFloat() / 180f
            bone.rotZ = animatable.visualRoll * Math.PI.toFloat() / 180f
            bone.rotY = 0f
        }
    }
}

class UavRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<UavEntity>(context, UavGeoModel()) {

    override fun applyRotations(
        animatable: UavEntity,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float,
        nativeScale: Float,
    ) {
        super.applyRotations(
            animatable,
            poseStack,
            ageInTicks,
            Mth.rotLerp(partialTick, animatable.yRotO, animatable.yRot),
            partialTick,
            nativeScale,
        )
    }
}

class UavStationGeoModel : GeoModel<GenericUavStationBlockEntity>() {
    override fun getModelResource(animatable: GenericUavStationBlockEntity): ResourceLocation =
        animatable.uavType.spec.visuals.model

    override fun getTextureResource(animatable: GenericUavStationBlockEntity): ResourceLocation =
        animatable.uavType.spec.visuals.texture

    override fun getAnimationResource(animatable: GenericUavStationBlockEntity): ResourceLocation =
        animatable.uavType.spec.visuals.animation
}

class UavStationRenderer(context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<GenericUavStationBlockEntity>(UavStationGeoModel()) {

    init {
        withScale(1.4f)
    }
}

class UavStationBlockItem(
    block: Block,
    properties: Properties,
    val uavType: UavType,
) : BlockItem(block, properties), GeoItem {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    init {
        SingletonGeoAnimatable.registerSyncedAnimatable(this)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun createGeoRenderer(consumer: Consumer<GeoRenderProvider>) {
        consumer.accept(object : GeoRenderProvider {
            private val renderer: GeoItemRenderer<UavStationBlockItem> by lazy {
                UavStationItemRenderer(uavType)
            }

            override fun getGeoItemRenderer(): GeoItemRenderer<UavStationBlockItem> = renderer
        })
    }
}

class UavStationItemRenderer(uavType: UavType) :
    GeoItemRenderer<UavStationBlockItem>(UavStationItemModel(uavType)) {

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
        packedOverlay: Int,
    ) {
        poseStack.pushPose()

        when (displayContext) {
            ItemDisplayContext.GUI -> {
                poseStack.translate(0f, -1.75f / 16f, 0f)
                poseStack.rotateAround(Axis.YP.rotationDegrees(45f), CENTER_X, CENTER_Y, CENTER_Z)
                poseStack.rotateAround(Axis.XP.rotationDegrees(25f), CENTER_X, CENTER_Y, CENTER_Z)
            }
            ItemDisplayContext.GROUND -> poseStack.scale(0.9f, 0.9f, 0.9f)
            ItemDisplayContext.FIXED -> {
                poseStack.translate(0f, -0.103f, 0.034f)
                poseStack.scale(1.1f, 1.1f, 1.1f)
                poseStack.rotateAround(
                    Quaternionf().rotationX(Math.toRadians(65.0).toFloat()),
                    CENTER_X,
                    CENTER_Y,
                    CENTER_Z,
                )
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
            else -> poseStack.scale(0.9f, 0.9f, 0.9f)
        }

        super.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)
        poseStack.popPose()
    }
}

class UavStationItemModel(private val uavType: UavType) : GeoModel<UavStationBlockItem>() {
    override fun getModelResource(animatable: UavStationBlockItem): ResourceLocation =
        uavType.spec.visuals.model

    override fun getTextureResource(animatable: UavStationBlockItem): ResourceLocation =
        uavType.spec.visuals.texture

    override fun getAnimationResource(animatable: UavStationBlockItem): ResourceLocation =
        uavType.spec.visuals.animation
}
