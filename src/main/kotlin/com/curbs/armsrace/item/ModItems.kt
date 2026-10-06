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
}
