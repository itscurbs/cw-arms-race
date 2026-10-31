package com.curbs.armsrace.recipe

import com.curbs.armsrace.CwArmsRace
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

object ModRecipes {

    private val TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, CwArmsRace.ID)
    private val SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, CwArmsRace.ID)

    val KILN_TYPE: DeferredHolder<RecipeType<*>, RecipeType<KilnRecipe>> = TYPES.register("kiln", Supplier {
        RecipeType.simple(id("kiln"))
    })

    val KILN_SERIALIZER: DeferredHolder<RecipeSerializer<*>, RecipeSerializer<KilnRecipe>> =
        SERIALIZERS.register("kiln", Supplier {
            StandardProcessingRecipe.Serializer<KilnRecipe> { KilnRecipe(it) }
        })

    val WEAVING_TYPE: DeferredHolder<RecipeType<*>, RecipeType<WeavingRecipe>> = TYPES.register("weaving", Supplier {
        RecipeType.simple(id("weaving"))
    })

    val WEAVING_SERIALIZER: DeferredHolder<RecipeSerializer<*>, RecipeSerializer<WeavingRecipe>> =
        SERIALIZERS.register("weaving", Supplier {
            StandardProcessingRecipe.Serializer<WeavingRecipe> { WeavingRecipe(it) }
        })

    val KILN: IRecipeTypeInfo = RecipeInfo(id("kiln"), KILN_TYPE, KILN_SERIALIZER)
    val WEAVING: IRecipeTypeInfo = RecipeInfo(id("weaving"), WEAVING_TYPE, WEAVING_SERIALIZER)

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, path)

    fun register() {
        TYPES.register(MOD_BUS)
        SERIALIZERS.register(MOD_BUS)
    }

    private class RecipeInfo(
        private val id: ResourceLocation,
        private val type: DeferredHolder<RecipeType<*>, *>,
        private val serializer: DeferredHolder<RecipeSerializer<*>, *>
    ) : IRecipeTypeInfo {

        override fun getId(): ResourceLocation = id

        @Suppress("UNCHECKED_CAST")
        override fun <T : RecipeSerializer<*>> getSerializer(): T = serializer.get() as T

        @Suppress("UNCHECKED_CAST")
        override fun <I : RecipeInput, R : Recipe<I>> getType(): RecipeType<R> = type.get() as RecipeType<R>
    }
}
