package com.curbs.armsrace.recipe

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams

class WeavingRecipe(params: ProcessingRecipeParams) : SimpleProcessingRecipe(ModRecipes.WEAVING, params) {

    override fun canSpecifyDuration(): Boolean = true

    override fun getMaxInputCount(): Int = 1

    override fun getMaxOutputCount(): Int = 2
}
