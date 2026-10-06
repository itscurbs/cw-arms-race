package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object ModCreativeModeTabs {
    val BASE_TAB = CwArmsRace.REGISTRATE
        .defaultCreativeTab("base_tab") { builder ->
            builder.title(Component.translatable("itemGroup.armsrace.base_tab"))
                .icon { ItemStack(ModItems.FPV_DRONE.get()) }
        }
        .register()
}
