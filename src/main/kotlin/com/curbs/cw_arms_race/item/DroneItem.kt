package com.curbs.cw_arms_race.item

import com.curbs.cw_arms_race.entity.DroneEntity
import com.curbs.cw_arms_race.entity.ModEntities
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
        val player = context.player
        val serverLevel = context.level as? ServerLevel ?: return InteractionResult.PASS

        val stack = context.itemInHand

        // atBottomCenterOf gives the bottom-centre of a block, which is exactly
        // where an entity's feet sit. Using .above() puts it on the top surface
        // of the clicked block rather than inside it.
        val spawnPos = Vec3.atBottomCenterOf(context.clickedPos.above())

        val drone = DroneEntity(ModEntities.FPV_DRONE, serverLevel)
        drone.setPos(spawnPos.x, spawnPos.y, spawnPos.z)

        // Actually put the entity into the world.
        serverLevel.addFreshEntity(drone)

        // Use up one drone. Does not shrink the stack in creative mode.
        stack.consume(1, player)

        return InteractionResult.SUCCESS
    }
}
