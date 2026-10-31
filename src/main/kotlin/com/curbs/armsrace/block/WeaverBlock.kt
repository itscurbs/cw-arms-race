package com.curbs.armsrace.block

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.foundation.block.IBE
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class WeaverBlock(properties: Properties) : KineticBlock(properties), IBE<WeaverBlockEntity> {

    override fun getRotationAxis(state: BlockState): Direction.Axis = Direction.Axis.Y

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return face == Direction.DOWN
    }

    override fun getBlockEntityClass(): Class<WeaverBlockEntity> = WeaverBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out WeaverBlockEntity> = ModBlockEntities.WEAVER.get()
}
