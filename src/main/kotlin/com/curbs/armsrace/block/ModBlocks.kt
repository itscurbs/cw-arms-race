package com.curbs.armsrace.block

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.util.entry.BlockEntry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks

object ModBlocks {
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
