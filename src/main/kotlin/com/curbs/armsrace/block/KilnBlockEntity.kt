package com.curbs.armsrace.block

import com.curbs.armsrace.recipe.ModRecipes
import com.curbs.armsrace.recipe.SimpleProcessingRecipe
import com.simibubi.create.content.processing.basin.BasinBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel
import com.simibubi.create.content.processing.recipe.HeatCondition
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class KilnBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    ProcessingKineticBlockEntity(type, pos, state, 2, 4) {

    override fun findRecipe(): SimpleProcessingRecipe? = findRecipe(ModRecipes.KILN_TYPE.get())

    override fun canProcess(recipe: SimpleProcessingRecipe): Boolean {
        return recipe.requiredHeat.testBlazeBurner(heatLevel())
    }

    private fun heatLevel(): HeatLevel {
        val level = level ?: return HeatLevel.NONE
        return BasinBlockEntity.getHeatLevelOf(level.getBlockState(worldPosition.below()))
    }

    override fun spawnParticles() {
        val level = level ?: return
        val random = level.random
        val center = Vec3.atCenterOf(worldPosition)

        repeat(2) {
            level.addParticle(
                ParticleTypes.SMOKE,
                center.x + (random.nextDouble() - 0.5) * 0.6,
                center.y + 0.5,
                center.z + (random.nextDouble() - 0.5) * 0.6,
                0.0,
                0.02,
                0.0
            )
        }

        if (lastRecipe?.requiredHeat == HeatCondition.SUPERHEATED && random.nextFloat() < 0.3f) {
            level.addParticle(
                ParticleTypes.FLAME,
                center.x + (random.nextDouble() - 0.5) * 0.4,
                center.y + 0.4,
                center.z + (random.nextDouble() - 0.5) * 0.4,
                0.0,
                0.01,
                0.0
            )
        }
    }
}
