package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.function.Consumer


class ControllerItem(
    properties: Item.Properties
) : Item(properties), GeoItem {

    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {

    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return cache
    }

    override fun createGeoRenderer(consumer: Consumer<GeoRenderProvider>) {
        consumer.accept(object : GeoRenderProvider {
            private val renderer: GeoItemRenderer<ControllerItem> by lazy { ControllerItemRenderer() }

            override fun getGeoItemRenderer(): GeoItemRenderer<ControllerItem> = renderer
        })
    }
}

class ControllerItemRenderer : GeoItemRenderer<ControllerItem>(ControllerItemModel()) {
    private companion object {
        const val MODEL_SCALE = 0.4f
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
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE)

        super.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)
        poseStack.popPose()
    }
}

class ControllerItemModel : GeoModel<ControllerItem>() {
    override fun getModelResource(animatable: ControllerItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/controller.geo.json")

    override fun getTextureResource(animatable: ControllerItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "textures/item/controller.png")

    override fun getAnimationResource(animatable: ControllerItem): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/controller.geo.json")
}
