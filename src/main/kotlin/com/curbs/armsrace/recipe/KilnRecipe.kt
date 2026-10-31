package com.curbs.armsrace.recipe

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams

class KilnRecipe(params: ProcessingRecipeParams) : SimpleProcessingRecipe(ModRecipes.KILN, params) {

    override fun canRequireHeat(): Boolean = true

    override fun canSpecifyDuration(): Boolean = true

    override fun getMaxInputCount(): Int = 2

    override fun getMaxOutputCount(): Int = 4
}
