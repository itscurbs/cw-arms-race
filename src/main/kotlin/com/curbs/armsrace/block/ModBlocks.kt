package com.curbs.armsrace.block

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.item.DroneBlockItem
import com.tterrag.registrate.util.entry.BlockEntry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.client.model.generators.ModelFile

object ModBlocks {
    val FPV_DRONE: BlockEntry<DroneBlock> = CwArmsRace.REGISTRATE
        .block("fpv_drone", ::DroneBlock)
        .initialProperties { Blocks.IRON_BLOCK }
        .properties { it.noOcclusion() }
        .blockstate { ctx, prov ->
            val model = prov.models()
                .getBuilder("fpv_drone")
                .texture("particle", prov.mcLoc("block/stone"))
            prov.simpleBlock(ctx.getEntry(), model)
        }
        .item(::DroneBlockItem)
        .model { ctx, prov -> prov.getBuilder(ctx.name).parent(ModelFile.UncheckedModelFile(prov.mcLoc("builtin/entity"))) }
        .build()
        .lang("FPV Drone")
        .register()

    val KILN: BlockEntry<KilnBlock> = CwArmsRace.REGISTRATE
        .block("kiln", ::KilnBlock)
        .initialProperties { Blocks.IRON_BLOCK }
        .item()
        .model { ctx, prov -> prov.withExistingParent(ctx.name, blockModel("kiln")) }
        .build()
        .lang("Kiln")
        .register()

    val WEAVER: BlockEntry<WeaverBlock> = CwArmsRace.REGISTRATE
        .block("weaver", ::WeaverBlock)
        .initialProperties { Blocks.IRON_BLOCK }
        .item()
        .model { ctx, prov -> prov.withExistingParent(ctx.name, blockModel("weaver")) }
        .build()
        .lang("Weaver")
        .register()

    private fun blockModel(name: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "block/$name")
}
