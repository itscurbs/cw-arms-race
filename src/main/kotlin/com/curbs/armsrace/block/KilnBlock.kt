package com.curbs.armsrace.block

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.foundation.block.IBE
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty

class KilnBlock(properties: Properties) : KineticBlock(properties), IBE<KilnBlockEntity> {
    companion object {
        val ACTIVE: BooleanProperty = BooleanProperty.create("active")
        val INNER: BooleanProperty = BooleanProperty.create("inner")
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(INNER, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState>) {
        builder.add(ACTIVE, INNER)
    }

    override fun getRotationAxis(state: BlockState): Direction.Axis = Direction.Axis.Y

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return face == Direction.UP
    }

    override fun getBlockEntityClass(): Class<KilnBlockEntity> = KilnBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out KilnBlockEntity> = ModBlockEntities.KILN.get()
}
