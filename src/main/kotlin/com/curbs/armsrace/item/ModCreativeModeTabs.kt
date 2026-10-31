package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import net.minecraft.world.item.ItemStack

object ModCreativeModeTabs {
    val BASE_TAB = CwArmsRace.REGISTRATE
        .defaultCreativeTab("arms_race") { builder ->
            builder.icon { ItemStack(ModItems.FPV_DRONE.get()) }
        }
        .register()
}
