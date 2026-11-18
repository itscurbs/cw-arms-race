package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.uav.FpvDrone
import net.minecraft.world.item.ItemStack

object ModCreativeModeTabs {
    val BASE_TAB = CwArmsRace.REGISTRATE
        .defaultCreativeTab("arms_race") { builder ->
            builder.icon { ItemStack(FpvDrone.TYPE.block!!.get()) }
        }
        .register()
}
