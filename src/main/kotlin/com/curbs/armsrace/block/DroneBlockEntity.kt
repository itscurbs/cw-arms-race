package com.curbs.armsrace.block

import com.curbs.armsrace.api.uav.UavStationBlockEntity
import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.ModEntities
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID

class DroneBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : UavStationBlockEntity(type, pos, state), GeoBlockEntity {

    override fun createUav(level: ServerLevel, player: ServerPlayer): DroneEntity? {
        val drone = ModEntities.FPV_DRONE.get().create(level) ?: return null
        val position = Vec3.atCenterOf(worldPosition.above())
        drone.moveTo(position.x, position.y, position.z, player.yRot, 0f)
        return drone
    }

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
    }

}
