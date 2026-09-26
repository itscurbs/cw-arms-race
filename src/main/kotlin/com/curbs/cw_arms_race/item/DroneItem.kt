package com.curbs.cw_arms_race.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.SmallFireball
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * The FPV drone item. Right-clicking launches it.
 *
 * Right now it launches a vanilla fireball as a placeholder, purely to prove that
 * right-click spawning works. Once `DroneEntity` exists we swap that fireball out
 * for the real drone.
 */
class DroneItem(properties: Item.Properties) : Item(properties) {

    /**
     * Called when a player right-clicks with this item.
     *
     * Note: this runs on BOTH the client and the server, so we must only touch
     * the world on the server side, otherwise the two sides desync.
     */
    override fun use(
        level: Level,
        player: Player,
        hand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)

        // Cast to ServerLevel. On the client this returns null and we bail out early.
        val serverLevel = level as? ServerLevel ?: return InteractionResultHolder.pass(stack)

        // The direction the player is looking. The drone launches along this.
        val direction = player.lookAngle

        // Spawn point in front of the player's eyes, so it never spawns inside them.
        val spawnPos = player.eyePosition.add(direction.scale(0.5))

        val fireball = SmallFireball(serverLevel, spawnPos.x, spawnPos.y, spawnPos.z, direction)

        // The constructor only nudges it along, so set the actual launch speed here.
        fireball.deltaMovement = direction.scale(1.5)

        // Remember who launched it, so it can damage them too (self-kill).
        fireball.setOwner(player)

        // Actually put the entity into the world.
        serverLevel.addFreshEntity(fireball)

        // Use up one drone. Does not shrink the stack in creative mode.
        stack.consume(1, player)

        return InteractionResultHolder.success(stack)
    }
}
