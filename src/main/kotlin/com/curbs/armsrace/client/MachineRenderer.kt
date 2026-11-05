package com.curbs.armsrace.client

import com.curbs.armsrace.block.KilnBlock
import com.curbs.armsrace.block.KilnBlockEntity
import com.curbs.armsrace.block.WeaverBlock
import com.curbs.armsrace.block.WeaverBlockEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import net.createmod.catnip.render.CachedBuffers
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

class KilnRenderer(context: BlockEntityRendererProvider.Context) :
    SafeBlockEntityRenderer<KilnBlockEntity>() {

    override fun renderSafe(
        be: KilnBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val state = be.blockState
            .setValue(KilnBlock.INNER, true)
            .setValue(KilnBlock.ACTIVE, be.isProcessing())
        KineticBlockEntityRenderer.renderRotatingBuffer(
            be,
            CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, state),
            poseStack,
            buffer.getBuffer(RenderType.solid()),
            LightTexture.FULL_BRIGHT
        )
    }
}

class WeaverRenderer(context: BlockEntityRendererProvider.Context) :
    SafeBlockEntityRenderer<WeaverBlockEntity>() {

    override fun renderSafe(
        be: WeaverBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val state = be.blockState
            .setValue(WeaverBlock.INNER, true)
            .setValue(WeaverBlock.ACTIVE, be.isProcessing())
        KineticBlockEntityRenderer.renderRotatingBuffer(
            be,
            CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, state),
            poseStack,
            buffer.getBuffer(RenderType.solid()),
            LightTexture.FULL_BRIGHT
        )
    }
}
