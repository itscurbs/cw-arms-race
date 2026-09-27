package com.curbs.cw_arms_race.entity

import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID

data class DroneInputPayload(
    val forward: Boolean,
    val backward: Boolean,
    val left: Boolean,
    val right: Boolean
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DroneInputPayload> {
        return TYPE
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<DroneInputPayload>(
            ResourceLocation.fromNamespaceAndPath("cw_arms_race", "drone_input")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, DroneInputPayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL, DroneInputPayload::forward,
                ByteBufCodecs.BOOL, DroneInputPayload::backward,
                ByteBufCodecs.BOOL, DroneInputPayload::left,
                ByteBufCodecs.BOOL, DroneInputPayload::right,
                ::DroneInputPayload
            )
    }
}

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

    var forward = false
    var backward = false
    var left = false
    var right = false

    var ownerId: UUID? = null



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
    override fun tick() {;
        super.tick()

        if (!level().isClientSide) {
            var x = 0.0
            var z = 0.0

            if (forward) z += 1.0
            if (backward) z -= 1.0
            if (left) x -= 1.0
            if (right) x += 1.0

            val input = Vec3(x, 0.0, z)

            if (input.lengthSqr() > 0.0) {
                val velocity = input.normalize().scale(0.1)

                deltaMovement = velocity
                move(MoverType.SELF, velocity)
            } else {
                deltaMovement = Vec3.ZERO
            }
        } else {
            val mc = Minecraft.getInstance()
            PacketDistributor.sendToServer(
                DroneInputPayload(
                    mc.options.keyUp.isDown,
                    mc.options.keyDown.isDown,
                    mc.options.keyLeft.isDown,
                    mc.options.keyRight.isDown,
                )
            )
        }
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
