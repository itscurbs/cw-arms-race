package com.curbs.armsrace.block

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.util.entry.BlockEntityEntry

object ModBlockEntities {
    val KILN: BlockEntityEntry<KilnBlockEntity> = CwArmsRace.REGISTRATE
        .blockEntity("kiln") { type, pos, state -> KilnBlockEntity(type, pos, state) }
        .validBlocks(ModBlocks.KILN)
        .register()

    val WEAVER: BlockEntityEntry<WeaverBlockEntity> = CwArmsRace.REGISTRATE
        .blockEntity("weaver") { type, pos, state -> WeaverBlockEntity(type, pos, state) }
        .validBlocks(ModBlocks.WEAVER)
        .register()
}
