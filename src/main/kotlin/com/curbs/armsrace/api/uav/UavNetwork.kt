package com.curbs.armsrace.api.uav

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

interface UavReconcilable {
    fun reconcile(
        position: Vec3,
        velocity: Vec3,
        acknowledgedInput: Int,
        confirmedImpact: Boolean,
    )
}

data class UavInputPayload(
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
    val seq: Int,
    val throttle: Float = 0f,
    val action: Int = 0,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<UavInputPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<UavInputPayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "uav_input")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, UavInputPayload> =
            object : StreamCodec<ByteBuf, UavInputPayload> {
                override fun encode(buf: ByteBuf, value: UavInputPayload) {
                    ByteBufCodecs.FLOAT.encode(buf, value.move.x)
                    ByteBufCodecs.FLOAT.encode(buf, value.move.y)
                    ByteBufCodecs.FLOAT.encode(buf, value.yaw)
                    ByteBufCodecs.FLOAT.encode(buf, value.pitch)
                    ByteBufCodecs.VAR_INT.encode(buf, value.seq)
                    ByteBufCodecs.FLOAT.encode(buf, value.throttle)
                    ByteBufCodecs.VAR_INT.encode(buf, value.action)
                }

                override fun decode(buf: ByteBuf): UavInputPayload = UavInputPayload(
                    Vec2(
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                    ),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                )
            }
    }
}

data class UavStatePayload(
    val uavId: Int,
    val seq: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val vz: Double = 0.0,
    val confirmedImpact: Boolean = false,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<UavStatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<UavStatePayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "uav_state")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, UavStatePayload> =
            object : StreamCodec<ByteBuf, UavStatePayload> {
                override fun encode(buf: ByteBuf, value: UavStatePayload) {
                    ByteBufCodecs.VAR_INT.encode(buf, value.uavId)
                    ByteBufCodecs.VAR_INT.encode(buf, value.seq)
                    ByteBufCodecs.DOUBLE.encode(buf, value.x)
                    ByteBufCodecs.DOUBLE.encode(buf, value.y)
                    ByteBufCodecs.DOUBLE.encode(buf, value.z)
                    ByteBufCodecs.DOUBLE.encode(buf, value.vx)
                    ByteBufCodecs.DOUBLE.encode(buf, value.vy)
                    ByteBufCodecs.DOUBLE.encode(buf, value.vz)
                    ByteBufCodecs.BOOL.encode(buf, value.confirmedImpact)
                }

                override fun decode(buf: ByteBuf): UavStatePayload = UavStatePayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                )
            }
    }
}

data class UavCameraPayload(
    val uavId: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<UavCameraPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<UavCameraPayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "uav_camera")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, UavCameraPayload> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                UavCameraPayload::uavId,
                ::UavCameraPayload,
            )
    }
}
