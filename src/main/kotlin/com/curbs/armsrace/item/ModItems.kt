package com.curbs.armsrace.item

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.util.entry.ItemEntry
import net.minecraft.world.item.Item
import net.neoforged.neoforge.client.model.generators.ModelFile

object ModItems {
    val FPV_DRONE: ItemEntry<DroneItem> = CwArmsRace.REGISTRATE
        .item("fpv_drone") { DroneItem(Item.Properties().stacksTo(1)) }
        .lang("FPV Drone")
        .model { ctx, prov -> prov.getBuilder(ctx.name).parent(ModelFile.UncheckedModelFile("builtin/entity")) }
        .register()

    val COAL_DUST: ItemEntry<CoalDustItem> = CwArmsRace.REGISTRATE
        .item("coal_dust") { CoalDustItem(Item.Properties().stacksTo(64)) }
        .lang("Coal Dust")
        .register()

    val COAL_PITCH: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("coal_pitch") { Item(Item.Properties()) }
        .lang("Coal Pitch")
        .register()

    val COKE_DUST: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("coke_dust") { Item(Item.Properties()) }
        .lang("Coke Dust")
        .register()

    val PITCH_FIBER: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("pitch_fiber") { Item(Item.Properties()) }
        .lang("Pitch Fiber")
        .register()

    val STABILIZED_FIBER: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("stabilized_fiber") { Item(Item.Properties()) }
        .lang("Stabilized Fiber")
        .register()

    val CARBON_FIBER_TOW: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("carbon_fiber_tow") { Item(Item.Properties()) }
        .lang("Carbon Fiber Tow")
        .register()

    val CARBON_FIBER_CLOTH: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("carbon_fiber_cloth") { Item(Item.Properties()) }
        .lang("Carbon Fiber Cloth")
        .register()

    val CARBON_FIBER_SHEET: ItemEntry<Item> = CwArmsRace.REGISTRATE
        .item("carbon_fiber_sheet") { Item(Item.Properties()) }
        .lang("Carbon Fiber Sheet")
        .register()
}
