package com.curbs.armsrace.recipe

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.wrapper.RecipeWrapper

abstract class SimpleProcessingRecipe(typeInfo: IRecipeTypeInfo, params: ProcessingRecipeParams) :
    StandardProcessingRecipe<RecipeWrapper>(typeInfo, params) {

    override fun matches(input: RecipeWrapper, level: Level): Boolean {
        if (input.isEmpty || ingredients.isEmpty()) return false

        val claimed = BooleanArray(input.size())

        for (ingredient in ingredients) {
            if (!claim(input, ingredient, claimed)) return false
        }

        for (slot in 0 until input.size()) {
            if (claimed[slot]) continue
            val stack = input.getItem(slot)
            if (stack.isEmpty) continue
            if (ingredients.none { it.test(stack) }) return false
        }

        return true
    }

    private fun claim(input: RecipeWrapper, ingredient: Ingredient, claimed: BooleanArray): Boolean {
        for (slot in 0 until input.size()) {
            if (claimed[slot]) continue
            val stack = input.getItem(slot)
            if (stack.isEmpty || !ingredient.test(stack)) continue
            claimed[slot] = true
            return true
        }
        return false
    }
}
