package com.curbs.cw_arms_race.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * The FPV drone entity. Behaviour is still a STUB - it does not move yet.
 *
 * Flight physics, collision and detonation come later; see the doc comments on
 * each method below for where that logic belongs.
 */
class DroneEntity(
    type: EntityType<out DroneEntity>,
    level: Level
) : Entity(type, level), GeoEntity {

    /**
     * Geckolib's per-entity animation state store. Created from `this`, which is
     * safe because GeckoLib looks the cache up by entity UUID, not by identity.
     */
    private val animatableCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animatableCache

    /**
     * No .animations.json exists yet, so there are no controllers to register.
     * The interface still requires the method. Animation controllers get added
     * here once the drone flies and its props spin.
     */
    override fun registerControllers(registrar: AnimatableManager.ControllerRegistrar) {
    }

    /**
     * Runs 20 times a second while the entity exists. This is where the flight
     * model (gravity + throttle thrust + drag) and the block/entity collision
     * checks that trigger detonation will go.
     */
    override fun tick() {
    }

    /**
     * Declares the values that get synced to clients, e.g. owner UUID, throttle
     * and battery. Each one is registered with `SynchedEntityData.defineId(...)`
     * and then read/written through `this.entityData`.
     */
    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
    }

    /** Writes our custom data to NBT so it survives a world save. */
    override fun addAdditionalSaveData(tag: CompoundTag) {
    }

    /** Reads back what [addAdditionalSaveData] wrote. */
    override fun readAdditionalSaveData(tag: CompoundTag) {
    }
}
