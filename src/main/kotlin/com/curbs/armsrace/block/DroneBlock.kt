package com.curbs.armsrace.block

import com.curbs.armsrace.item.ControllerItem
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class DroneBlock(properties: Properties) : BaseEntityBlock(properties) {

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec(::DroneBlock)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        DroneBlockEntity(ModBlockEntities.FPV_DRONE.get(), pos, state)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = SHAPE

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape =
        Shapes.empty()

    override fun getShadeBrightness(state: BlockState, level: BlockGetter, pos: BlockPos): Float = 1.0f

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        if (!level.isClientSide && !state.`is`(newState.block)) {
            val controllerId = (level.getBlockEntity(pos) as? DroneBlockEntity)?.controllerId
            val server = level.server
            if (controllerId != null && server != null) {
                ControllerItem.clearLink(server, controllerId)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    companion object {
        private val SHAPE: VoxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
    }
}
