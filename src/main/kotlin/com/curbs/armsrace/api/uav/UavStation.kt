package com.curbs.armsrace.api.uav

import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.entry.BlockEntry
import com.tterrag.registrate.util.entry.EntityEntry
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID

interface UavLinkedItem {
    fun clearLink(stack: ItemStack, controllerId: UUID): Boolean
}

object UavLinks {
    fun clear(server: MinecraftServer, controllerId: UUID) {
        for (player in server.playerList.players) {
            var changed = false
            for (stack in player.inventory.items) {
                if (clearFrom(stack, controllerId)) changed = true
            }
            for (stack in player.inventory.offhand) {
                if (clearFrom(stack, controllerId)) changed = true
            }
            if (changed) player.containerMenu.broadcastChanges()
        }
    }

    private fun clearFrom(stack: ItemStack, controllerId: UUID): Boolean {
        val item = stack.item as? UavLinkedItem ?: return false
        return item.clearLink(stack, controllerId)
    }
}

class UavType internal constructor(
    val name: String,
    val spec: UavSpec,
    val entity: EntityEntry<UavEntity>,
) {
    var block: BlockEntry<GenericUavStationBlock>? = null
    var blockEntity: BlockEntityEntry<GenericUavStationBlockEntity>? = null

    fun spawn(level: ServerLevel, position: Vec3, yaw: Float): UavEntity? {
        val uav = entity.get().create(level) as? UavEntity ?: return null
        uav.moveTo(position.x, position.y, position.z, yaw, 0f)
        uav.home = position
        return uav
    }
}

open class UavStationBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(type, pos, state) {
    var controllerId: UUID? = null
        set(value) {
            if (field == value) return
            field = value
            setChanged()
        }

    open fun createUav(level: ServerLevel, player: ServerPlayer?): Entity? = null

    fun deployAutonomous(level: ServerLevel, owner: UUID?, target: Vec3?): Entity? {
        val entity = createUav(level, null) ?: return null
        if (entity is UavEntity) {
            entity.ownerId = owner
            entity.target = target
        }
        return if (level.addFreshEntity(entity)) entity else null
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        controllerId?.let { tag.putUUID("Controller", it) }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        controllerId = if (tag.hasUUID("Controller")) tag.getUUID("Controller") else null
    }
}

class GenericUavStationBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
    val uavType: UavType,
) : UavStationBlockEntity(type, pos, state), GeoBlockEntity {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun createUav(level: ServerLevel, player: ServerPlayer?): Entity? =
        uavType.spawn(level, Vec3.atCenterOf(worldPosition.above()), player?.yRot ?: 0f)

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
    }
}

abstract class UavStationBlock(
    properties: Properties,
    private val shape: VoxelShape = Shapes.block(),
) : BaseEntityBlock(properties) {

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shape

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape =
        Shapes.empty()

    override fun getShadeBrightness(state: BlockState, level: BlockGetter, pos: BlockPos): Float = 1.0f

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean,
    ) {
        if (!level.isClientSide && !state.`is`(newState.block)) {
            val controllerId = (level.getBlockEntity(pos) as? UavStationBlockEntity)?.controllerId
            if (controllerId != null) {
                level.server?.let { UavLinks.clear(it, controllerId) }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}

class GenericUavStationBlock(
    properties: Properties,
    val uavType: UavType,
) : UavStationBlock(properties, Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)) {

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec { GenericUavStationBlock(it, uavType) }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        GenericUavStationBlockEntity(uavType.blockEntity?.get() ?: error("Station block entity not registered"), pos, state, uavType)
}
