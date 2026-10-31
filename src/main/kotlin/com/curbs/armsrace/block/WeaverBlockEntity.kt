package com.curbs.armsrace.block

import com.curbs.armsrace.recipe.ModRecipes
import com.curbs.armsrace.recipe.SimpleProcessingRecipe
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class WeaverBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    ProcessingKineticBlockEntity(type, pos, state, 1, 2) {

    override fun findRecipe(): SimpleProcessingRecipe? = findRecipe(ModRecipes.WEAVING_TYPE.get())

    override fun spawnParticles() {
        val level = level ?: return
        val random = level.random
        val center = Vec3.atCenterOf(worldPosition)

        level.addParticle(
            ParticleTypes.CRIT,
            center.x + (random.nextDouble() - 0.5) * 0.4,
            center.y + 0.4,
            center.z + (random.nextDouble() - 0.5) * 0.4,
            0.0,
            0.02,
            0.0
        )
    }
}
