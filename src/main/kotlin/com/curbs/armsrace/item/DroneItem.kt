package com.curbs.armsrace.item

import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

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
 * The FPV drone item. Right-clicking places a [DroneEntity].
 *
 * Right-clicking a block places the drone on top of that block.
 * Right-clicking air places it in front of the player.
 */
class DroneItem(properties: Item.Properties) : Item(properties) {

    /**
     * Called when the player right-clicks a BLOCK while holding this item.
     *
     * Note: this runs on BOTH the client and the server, so we must only touch
     * the world on the server side, otherwise the two sides desync.
     */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        val serverLevel = context.level as? ServerLevel ?: return InteractionResult.PASS

        val stack = context.itemInHand

        // atBottomCenterOf gives the bottom-centre of a block, which is exactly
        // where an entity's feet sit. Using .above() puts it on the top surface
        // of the clicked block rather than inside it.
        val spawnPos = Vec3.atBottomCenterOf(context.clickedPos.above())

        val drone = DroneEntity(ModEntities.FPV_DRONE, serverLevel)
        drone.setPos(spawnPos.x, spawnPos.y, spawnPos.z)

        drone.ownerId = player.uuid

        val pd = player.persistentData

        pd.putInt("Drone", drone.id)

        // Actually put the entity into the world.a
        serverLevel.addFreshEntity(drone)

        PacketDistributor.sendToPlayer(player, SetDroneCameraPayload(drone.id))

        // Use up one drone. Does not shrink the stack in creative mode.
        stack.consume(1, player)

        return InteractionResult.SUCCESS
    }
}
