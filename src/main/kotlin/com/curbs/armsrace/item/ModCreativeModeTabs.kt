package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.block.ModBlocks
import net.minecraft.world.item.ItemStack

object ModCreativeModeTabs {
    val BASE_TAB = CwArmsRace.REGISTRATE
        .defaultCreativeTab("arms_race") { builder ->
            builder.icon { ItemStack(ModBlocks.FPV_DRONE.get()) }
        }
        .register()
}
