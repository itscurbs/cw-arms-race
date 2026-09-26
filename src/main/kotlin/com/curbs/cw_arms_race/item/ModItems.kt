package com.curbs.cw_arms_race.item

import com.curbs.cw_arms_race.Cw_arms_race
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModItems {
    val REGISTRY = DeferredRegister.createItems(Cw_arms_race.ID)

    // The drone. Right-clicking launches it (see DroneItem).
    val FPV_DRONE by REGISTRY.register("fpv_drone") { ->
        DroneItem(Item.Properties().stacksTo(1))
    }
}
