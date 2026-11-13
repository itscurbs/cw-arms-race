package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.Optional
import java.util.UUID
import java.util.function.Supplier

data class DroneLink(
    val controllerId: UUID,
    val dimension: ResourceKey<Level>,
    val pos: BlockPos,
    val droneId: UUID? = null,
) {
    companion object {
        val CODEC: Codec<DroneLink> = RecordCodecBuilder.create { instance ->
            instance.group(
                UUIDUtil.CODEC.fieldOf("controller").forGetter(DroneLink::controllerId),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DroneLink::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(DroneLink::pos),
                UUIDUtil.CODEC.optionalFieldOf("drone").forGetter { Optional.ofNullable(it.droneId) },
            ).apply(instance) { controllerId, dimension, pos, droneId ->
                DroneLink(controllerId, dimension, pos, droneId.orElse(null))
            }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, DroneLink> = object : StreamCodec<ByteBuf, DroneLink> {
            override fun encode(buf: ByteBuf, value: DroneLink) {
                UUIDUtil.STREAM_CODEC.encode(buf, value.controllerId)
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, value.dimension)
                BlockPos.STREAM_CODEC.encode(buf, value.pos)
                ByteBufCodecs.BOOL.encode(buf, value.droneId != null)
                value.droneId?.let { UUIDUtil.STREAM_CODEC.encode(buf, it) }
            }

            override fun decode(buf: ByteBuf): DroneLink {
                val controllerId = UUIDUtil.STREAM_CODEC.decode(buf)
                val dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf)
                val pos = BlockPos.STREAM_CODEC.decode(buf)
                val droneId = if (ByteBufCodecs.BOOL.decode(buf)) UUIDUtil.STREAM_CODEC.decode(buf) else null
                return DroneLink(controllerId, dimension, pos, droneId)
            }
        }
    }
}

object ModDataComponents {
    private val REGISTER = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CwArmsRace.ID)

    val DRONE_LINK: Supplier<DataComponentType<DroneLink>> = REGISTER.register("drone_link", Supplier {
        DataComponentType.builder<DroneLink>()
            .persistent(DroneLink.CODEC)
            .networkSynchronized(DroneLink.STREAM_CODEC)
            .build()
    })

    init {
        REGISTER.register(MOD_BUS)
    }
}
