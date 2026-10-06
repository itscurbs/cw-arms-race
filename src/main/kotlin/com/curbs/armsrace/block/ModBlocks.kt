package com.curbs.armsrace.block

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.util.entry.BlockEntry
import net.minecraft.world.level.block.Blocks

object ModBlocks {
    val HIGH_TEMP_FURNACE: BlockEntry<HighTempFurnaceBlock> = CwArmsRace.REGISTRATE
        .block("high_temp_furnace", ::HighTempFurnaceBlock)
        .initialProperties { Blocks.IRON_BLOCK }
        .simpleItem()
        .lang("Ultra high-temperature furnace")
        .register()
}