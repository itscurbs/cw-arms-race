package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.api.uav.UavCameraPayload
import com.curbs.armsrace.api.uav.UavLinkable
import com.curbs.armsrace.api.uav.UavLinkedItem
import com.curbs.armsrace.api.uav.UavLinks
import com.curbs.armsrace.api.uav.UavStationBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.neoforged.neoforge.network.PacketDistributor
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID
import java.util.function.Consumer

class ControllerItem(
    properties: Item.Properties,
) : Item(properties), GeoItem, UavLinkedItem {

    private val cache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun createGeoRenderer(consumer: Consumer<GeoRenderProvider>) {
        consumer.accept(object : GeoRenderProvider {
            private val renderer: GeoItemRenderer<ControllerItem> by lazy { ControllerItemRenderer() }

            override fun getGeoItemRenderer(): GeoItemRenderer<ControllerItem> = renderer
        })
    }

    override fun clearLink(stack: ItemStack, controllerId: UUID): Boolean {
        val link = stack.get(ModDataComponents.DRONE_LINK.get()) ?: return false
        if (link.controllerId != controllerId) return false
        stack.remove(ModDataComponents.DRONE_LINK.get())
        return true
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val blockEntity = context.level.getBlockEntity(context.clickedPos) as? UavStationBlockEntity
            ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS

        val stack = context.itemInHand
        val existingLink = stack.get(ModDataComponents.DRONE_LINK.get())
        val existingDrone = existingLink?.droneId?.let { droneId ->
            val serverLevel = context.level as? ServerLevel
            serverLevel?.server?.getLevel(existingLink.dimension)?.getEntity(droneId)
                ?.takeUnless { it.isRemoved }
                ?.let { it as? UavLinkable }
        }
        if (existingDrone != null) return InteractionResult.FAIL
        if (blockEntity.controllerId != null && blockEntity.controllerId != existingLink?.controllerId) {
            return InteractionResult.FAIL
        }

        val controllerId = existingLink?.controllerId ?: UUID.randomUUID()
        val link = DroneLink(
            controllerId,
            context.level.dimension(),
            context.clickedPos,
        )
        stack.set(ModDataComponents.DRONE_LINK.get(), link)
        blockEntity.controllerId = controllerId
        player.swing(context.hand)
        return InteractionResult.SUCCESS
    }

    override fun use(
        level: net.minecraft.world.level.Level,
        player: net.minecraft.world.entity.player.Player,
        hand: InteractionHand,
    ): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)
        val serverPlayer = player as? ServerPlayer
            ?: return InteractionResultHolder.fail(stack)
        val link = stack.get(ModDataComponents.DRONE_LINK.get())
            ?: return InteractionResultHolder.fail(stack)
        val serverLevel = level as? ServerLevel
            ?: return InteractionResultHolder.fail(stack)
        if (link.dimension != serverLevel.dimension()) {
            return InteractionResultHolder.fail(stack)
        }

        if (serverPlayer.isShiftKeyDown) {
            val station = serverLevel.getBlockEntity(link.pos) as? UavStationBlockEntity
                ?: return InteractionResultHolder.fail(stack)
            val target = serverPlayer.pick(128.0, 0f, false).location
            station.deployAutonomous(serverLevel, serverPlayer.uuid, target)
                ?: return InteractionResultHolder.fail(stack)
            return InteractionResultHolder.success(stack)
        }

        val existing = link.droneId?.let { serverLevel.getEntity(it) as? UavLinkable }
        val drone = existing ?: spawnDrone(serverLevel, serverPlayer, link)
            ?: return InteractionResultHolder.fail(stack)
        if (drone.ownerId != null && drone.ownerId != serverPlayer.uuid) {
            return InteractionResultHolder.fail(stack)
        }

        drone.ownerId = serverPlayer.uuid
        drone.controllerId = link.controllerId
        val entity = drone as? net.minecraft.world.entity.Entity
            ?: return InteractionResultHolder.fail(stack)
        serverPlayer.persistentData.putUUID("Drone", entity.uuid)
        stack.set(ModDataComponents.DRONE_LINK.get(), link.copy(droneId = entity.uuid))
        PacketDistributor.sendToPlayer(serverPlayer, UavCameraPayload(entity.id))
        return InteractionResultHolder.success(stack)
    }

    private fun spawnDrone(level: ServerLevel, player: ServerPlayer, link: DroneLink): UavLinkable? {
        val station = level.getBlockEntity(link.pos) as? UavStationBlockEntity ?: return null
        val entity = station.createUav(level, player) as? UavLinkable ?: return null
        entity.ownerId = player.uuid
        entity.controllerId = link.controllerId
        val minecraftEntity = entity as? net.minecraft.world.entity.Entity ?: return null
        if (!level.addFreshEntity(minecraftEntity)) return null
        return entity
    }

    companion object {
        fun clearLink(server: MinecraftServer, controllerId: UUID) {
            UavLinks.clear(server, controllerId)
        }
    }
}

class ControllerItemRenderer : GeoItemRenderer<ControllerItem>(ControllerItemModel()) {
    private companion object {
        const val MODEL_SCALE = 1f
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
