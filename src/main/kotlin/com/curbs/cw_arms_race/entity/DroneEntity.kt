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
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.apache.logging.log4j.LogManager
import org.joml.Vector2f
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

data class DroneInputPayload(
    val move: Vec2,
    val mouse: Vec2,
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
                ByteBufCodecs.FLOAT,
                { it.move.x },
                ByteBufCodecs.FLOAT,
                { it.move.y },
                ByteBufCodecs.FLOAT,
                { it.mouse.x },
                ByteBufCodecs.FLOAT,
                { it.mouse.y }
            ) { movementX, movementY, mouseX, mouseY ->
                DroneInputPayload(
                    Vec2(movementX, movementY),
                    Vec2(mouseX, mouseY)
                )
            }
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


    var move: Vec2 = Vec2(0f, 0f)
    var mouse: Vec2 = Vec2(0f, 0f)

    var ownerId: UUID? = null

    companion object {
        private val LOGGER = LogManager.getLogger("cw_arms_race")
    }

    private fun onImpact() {
        LOGGER.info("Drone {} collided with block at {}", uuid, blockPosition())
        val pos = blockPosition()

        level().explode(null,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            16.0F,
            Level.ExplosionInteraction.TNT
        )

        discard()
    }



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

            val strafe = -move.x.toDouble()
            val forward = move.y.toDouble()

            val yaw = Math.toRadians(yRot.toDouble())
            val pitch = Math.toRadians(xRot.toDouble())

            val forwardX = -sin(yaw) * cos(pitch)
            val forwardY = -sin(pitch)
            val forwardZ = cos(yaw) * cos(pitch)

            val rightX = cos(yaw)
            val rightZ = sin(yaw)

            val x = forwardX * forward + rightX * strafe
            val y = forwardY * forward
            val z = forwardZ * forward + rightZ * strafe

            val movement = Vec3(x, y, z)

            if (movement.lengthSqr() > 0.0) {
                val velocity = movement.normalize().scale(0.5)

                deltaMovement = velocity
                move(MoverType.SELF, velocity)

                if (horizontalCollision || verticalCollision) {
                    onImpact()
                }
            }

            if (ownerId == null) {
                discard()
            }
        } else {
            val mc = Minecraft.getInstance()
            // Only pilot the drone we're actually looking through.
            // Otherwise every loaded drone spams the server each tick.
            if (mc.cameraEntity !== this) return
            val move = Vec2(
                (if (mc.options.keyRight.isDown) 1f else 0f) -
                        (if (mc.options.keyLeft.isDown) 1f else 0f),
                (if (mc.options.keyUp.isDown) 1f else 0f) -
                        (if (mc.options.keyDown.isDown) 1f else 0f),
            )
            val mouse = Vec2(
                mc.mouseHandler.xVelocity.toFloat(),
                mc.mouseHandler.yVelocity.toFloat(),
            )
            PacketDistributor.sendToServer(
                DroneInputPayload(
                    move,
                    mouse
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
