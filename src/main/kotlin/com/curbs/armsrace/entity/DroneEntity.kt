package com.curbs.armsrace.entity

import com.julian.createwarfare.effects.server.WaveEffect
import com.julian.createwarfare.explosions.types.GenericExplosion
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.apache.logging.log4j.LogManager
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

data class DroneInputPayload(
    val move: Vec2,
    val yaw: Float,
    val pitch: Float,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<DroneInputPayload> {
        return TYPE
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<DroneInputPayload>(
            ResourceLocation.fromNamespaceAndPath("armsrace", "drone_input")
        )

        val STREAM_CODEC: StreamCodec<ByteBuf, DroneInputPayload> =
            StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                { it.move.x },
                ByteBufCodecs.FLOAT,
                { it.move.y },
                ByteBufCodecs.FLOAT,
                { it.yaw },
                ByteBufCodecs.FLOAT,
                { it.pitch }
            ) { movementX, movementY, yaw, pitch ->
                DroneInputPayload(
                    Vec2(movementX, movementY),
                    yaw,
                    pitch,
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

    var ownerId: UUID? = null

    companion object {
        private val LOGGER = LogManager.getLogger("armsrace")
    }

    private fun onImpact() {
        LOGGER.info("Drone {} collided with block at {}", uuid, blockPosition())
        val pos = blockPosition()

        GenericExplosion.trigger(
            level(),
            blockPosition(),
            128.0F,
            8.0F,
            96.0F,
        )

        WaveEffect.start(
            level() as ServerLevel?,
            blockPosition(),
            2.0F,
            12.0F,
            0xffe3b8,
            0.0F,
        )



        discard()
    }

    /**
     * Frame-rate yaw/pitch for the piloted drone.
     * Takes already-scaled degree deltas (sensitivity applied by caller),
     * so this stays server-safe. Snaps yRotO/xRotO so the camera uses the
     * exact value this frame instead of lerping a tick behind.
     */
    fun applyRotation(yawDeltaDeg: Float, pitchDeltaDeg: Float) {
        setYRot(yRot + yawDeltaDeg)
        setXRot(Mth.clamp(xRot + pitchDeltaDeg, -90f, 90f))

        yRotO = yRot
        xRotO = xRot
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
            // Rotation is applied per-frame in CameraHandler.onCalculateTurn;
            // here we just send the absolute result so the server converges.
            PacketDistributor.sendToServer(
                DroneInputPayload(
                    move,
                    yRot,
                    xRot
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
