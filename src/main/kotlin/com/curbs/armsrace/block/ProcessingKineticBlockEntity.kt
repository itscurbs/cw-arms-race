package com.curbs.armsrace.block

import com.curbs.armsrace.recipe.SimpleProcessingRecipe
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.item.ItemHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemHandlerHelper
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper
import net.neoforged.neoforge.items.wrapper.RecipeWrapper
import kotlin.math.abs

abstract class ProcessingKineticBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
    inputSlots: Int,
    outputSlots: Int
) : KineticBlockEntity(type, pos, state) {

    val inputInv = ItemStackHandler(inputSlots)
    val outputInv = ItemStackHandler(outputSlots)
    val capability: IItemHandler = InventoryHandler()

    protected var timer = 0
    protected var lastRecipe: SimpleProcessingRecipe? = null

    protected abstract fun findRecipe(): SimpleProcessingRecipe?

    protected open fun canProcess(recipe: SimpleProcessingRecipe): Boolean = true

    protected open fun spawnParticles() {}

    override fun tick() {
        super.tick()
        val level = level ?: return

        if (level.isClientSide) {
            if (timer > 0) spawnParticles()
            return
        }

        if (abs(speed) < MIN_RPM) return

        if (timer > 0) {
            timer -= processingSpeed()
            if (timer <= 0) process()
            return
        }

        if (!hasOutputRoom()) return
        val recipe = findRecipe() ?: return
        if (!canProcess(recipe)) return

        lastRecipe = recipe
        timer = recipe.processingDuration
        sendData()
    }

    protected fun <R : SimpleProcessingRecipe> findRecipe(type: RecipeType<R>): R? {
        val level = level ?: return null
        val wrapper = RecipeWrapper(inputInv)
        if (wrapper.isEmpty) return null
        return level.recipeManager
            .getRecipeFor(type, wrapper, level)
            .map { it.value }
            .orElse(null)
    }

    private fun hasOutputRoom(): Boolean {
        return (0 until outputInv.slots).any { outputInv.getStackInSlot(it).count < outputInv.getSlotLimit(it) }
    }

    private fun process() {
        val level = level ?: return
        val recipe = lastRecipe ?: return
        val wrapper = RecipeWrapper(inputInv)

        if (!recipe.matches(wrapper, level)) {
            timer = 0
            lastRecipe = null
            return
        }

        consumeIngredients(recipe)
        recipe.rollResults(level.random)
            .forEach { ItemHandlerHelper.insertItemStacked(outputInv, it, false) }

        setChanged()
        sendData()
    }

    private fun consumeIngredients(recipe: SimpleProcessingRecipe) {
        for (ingredient in recipe.ingredients) {
            for (slot in 0 until inputInv.slots) {
                val stack = inputInv.getStackInSlot(slot)
                if (!stack.isEmpty && ingredient.test(stack)) {
                    inputInv.extractItem(slot, 1, false)
                    break
                }
            }
        }
    }

    private fun processingSpeed(): Int = Mth.clamp((abs(speed) / 16f).toInt(), 1, 512)

    override fun destroy() {
        super.destroy()
        level?.let { ItemHelper.dropContents(it, worldPosition, inputInv) }
        level?.let { ItemHelper.dropContents(it, worldPosition, outputInv) }
    }

    override fun write(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        compound.putInt("Timer", timer)
        compound.put("Input", inputInv.serializeNBT(registries))
        compound.put("Output", outputInv.serializeNBT(registries))
        super.write(compound, registries, clientPacket)
    }

    override fun read(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        timer = compound.getInt("Timer")
        inputInv.deserializeNBT(registries, compound.getCompound("Input"))
        outputInv.deserializeNBT(registries, compound.getCompound("Output"))
        super.read(compound, registries, clientPacket)
    }

    private inner class InventoryHandler : CombinedInvWrapper(inputInv, outputInv) {

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return outputInv != getHandlerFromIndex(getIndexForSlot(slot))
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (outputInv == getHandlerFromIndex(getIndexForSlot(slot))) return stack
            return super.insertItem(slot, stack, simulate)
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            if (inputInv == getHandlerFromIndex(getIndexForSlot(slot))) return ItemStack.EMPTY
            return super.extractItem(slot, amount, simulate)
        }
    }

    companion object {
        const val MIN_RPM = 32f
    }
}
