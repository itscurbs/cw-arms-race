package com.curbs.armsrace.item

import com.curbs.armsrace.client.DroneItemRenderer
import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.ModEntities
import com.google.common.base.Suppliers
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.SingletonGeoAnimatable
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.function.Consumer

/**
 * Packet telling the client which drone to use as the camera.
 */
data class SetDroneCameraPayload(
    val droneId: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SetDroneCameraPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SetDroneCameraPayload>(
            ResourceLocation.fromNamespaceAndPath(
                "armsrace",
                "set_drone_camera"
            )
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, SetDroneCameraPayload> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                SetDroneCameraPayload::droneId,
                ::SetDroneCameraPayload
            )
    }
}

/**
 * The FPV drone item.
 *
 * Right-clicking a block places a DroneEntity on top of it.
 * Right-clicking air is currently not handled here.
 */
class DroneItem(
    properties: Item.Properties
) : Item(properties), GeoItem {

    private val geoCache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    init {
        SingletonGeoAnimatable.registerSyncedAnimatable(this)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer
            ?: return InteractionResult.PASS

        val serverLevel = context.level as? ServerLevel
            ?: return InteractionResult.PASS

        val stack = context.itemInHand

        val spawnPos = Vec3.atBottomCenterOf(
            context.clickedPos.above()
        )

        val drone = DroneEntity(
            ModEntities.FPV_DRONE.get(),
            serverLevel
        )

        drone.setPos(
            spawnPos.x,
            spawnPos.y + 0.6,
            spawnPos.z
        )

        drone.simState.velocity = Vec3(
            0.0,
            0.9,
            0.0
        )

        drone.ownerId = player.uuid

        player.persistentData.putInt(
            "Drone",
            drone.id
        )

        serverLevel.addFreshEntity(drone)

        PacketDistributor.sendToPlayer(
            player,
            SetDroneCameraPayload(drone.id)
        )

        stack.consume(1, player)

        return InteractionResult.SUCCESS
    }

    override fun registerControllers(
        controllers: AnimatableManager.ControllerRegistrar
    ) {
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return geoCache
    }

    override fun createGeoRenderer(
        consumer: Consumer<GeoRenderProvider>
    ) {
        consumer.accept(
            object : GeoRenderProvider {

                private val renderer =
                    Suppliers.memoize<GeoItemRenderer<DroneItem>> {
                        DroneItemRenderer()
                    }

                override fun getGeoItemRenderer(): GeoItemRenderer<DroneItem> {
                    return renderer.get()
                }
            }
        )
    }
}