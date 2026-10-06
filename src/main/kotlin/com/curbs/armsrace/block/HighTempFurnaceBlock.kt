package com.curbs.armsrace.block

import com.simibubi.create.content.kinetics.base.KineticBlock
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

class HighTempFurnaceBlock(properties: Properties) : KineticBlock(properties) {
    override fun getRotationAxis(state: BlockState?): Direction.Axis { return Direction.Axis.Y }

}