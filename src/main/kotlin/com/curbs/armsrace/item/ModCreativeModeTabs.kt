package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModCreativeModeTabs {
    val REGISTRY: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CwArmsRace.ID)

    val BASE_TAB by REGISTRY.register("base_tab") { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.armsrace.base_tab"))
            .icon { ItemStack(ModItems.FPV_DRONE) }
            .displayItems { _, output ->
                output.accept(ModItems.FPV_DRONE)
            }
            .build()
    }
}
