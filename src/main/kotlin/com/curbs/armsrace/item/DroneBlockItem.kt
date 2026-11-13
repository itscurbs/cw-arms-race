package com.curbs.armsrace.item

import com.curbs.armsrace.client.DroneItemRenderer
import com.google.common.base.Suppliers
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.SingletonGeoAnimatable
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.function.Consumer

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

class DroneBlockItem(
    block: Block,
    properties: Item.Properties
) : BlockItem(block, properties), GeoItem {

    private val geoCache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    init {
        SingletonGeoAnimatable.registerSyncedAnimatable(this)
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
                    Suppliers.memoize<GeoItemRenderer<DroneBlockItem>> {
                        DroneItemRenderer()
                    }

                override fun getGeoItemRenderer(): GeoItemRenderer<DroneBlockItem> {
                    return renderer.get()
                }
            }
        )
    }
}
