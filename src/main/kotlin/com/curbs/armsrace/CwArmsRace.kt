package com.curbs.armsrace

import com.curbs.armsrace.client.DroneRenderer
import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.DroneInputPayload
import com.curbs.armsrace.entity.DroneStatePayload
import com.curbs.armsrace.entity.ModEntities
import com.curbs.armsrace.item.ModCreativeModeTabs
import com.curbs.armsrace.item.ModItems
import com.curbs.armsrace.item.SetDroneCameraPayload
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(CwArmsRace.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object CwArmsRace {
    const val ID = "armsrace"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        ModItems.REGISTRY.register(MOD_BUS)
        ModEntities.REGISTRY.register(MOD_BUS)
        ModCreativeModeTabs.REGISTRY.register(MOD_BUS)


        val obj = runForDist(clientTarget = {
            MOD_BUS.addListener(::onClientSetup)
            // Renderers are client-only. Registering one on a dedicated server
            // would crash, so this is deliberately inside the client branch.
            MOD_BUS.addListener(::registerRenderers)
            Minecraft.getInstance()
        }, serverTarget = {
            MOD_BUS.addListener(::onServerSetup)
            "test"
        })

        println(obj)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")

        NeoForge.EVENT_BUS.addListener(CameraHandler::onClientTick)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onRenderHand)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onCalculateTurn)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onFramePre)
    }

    /**
     * Binds a renderer to each of our entity types.
     *
     * This is not optional. Minecraft's EntityRenderDispatcher does not null
     * check unknown entity types, so spawning an entity with no registered
     * renderer throws an NPE on the render thread and hard-crashes the client.
     */
    private fun registerRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(ModEntities.FPV_DRONE, ::DroneRenderer)
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }

    @SubscribeEvent
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.playToServer(
            DroneInputPayload.TYPE,
            DroneInputPayload.STREAM_CODEC
        ) { payload, context ->
            // This runs on the SERVER
            context.enqueueWork {
                val player = context.player()

                if (!player.persistentData.contains("Drone")) {
                    return@enqueueWork
                }

                val droneId = player.persistentData.getInt("Drone")

                val drone = player.level().getEntity(droneId) as? DroneEntity ?: return@enqueueWork

                drone.inputQueue.addLast(com.curbs.armsrace.entity.DroneInputEntry(payload.seq, payload.move, payload.yaw, payload.pitch.coerceIn(-90f, 90f), net.minecraft.world.phys.Vec3.ZERO))
                if (drone.inputQueue.size > 64) {
                    drone.inputQueue.removeFirst()
                }
            }
        }

        registrar.playToClient(
            DroneStatePayload.TYPE,
            DroneStatePayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val mc = Minecraft.getInstance()
                val drone = mc.level?.getEntity(payload.droneId) as? DroneEntity ?: return@enqueueWork
                drone.reconcile(payload.x, payload.y, payload.z, payload.seq)
            }
        }

        registrar.playToClient(
            SetDroneCameraPayload.TYPE,
            SetDroneCameraPayload.STREAM_CODEC
        ) { payload, context ->
            context.enqueueWork {
                val mc = Minecraft.getInstance()
                val drone = mc.level?.getEntity(payload.droneId)

                if (drone is DroneEntity) {
                    mc.cameraEntity = drone
                }
            }
;        }
    }
}
