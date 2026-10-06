package com.curbs.armsrace

import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.DroneInputPayload
import com.curbs.armsrace.entity.DroneStatePayload
import com.curbs.armsrace.entity.ModEntities
import com.curbs.armsrace.item.ModCreativeModeTabs
import com.curbs.armsrace.item.ModItems
import com.curbs.armsrace.item.SetDroneCameraPayload
import com.simibubi.create.foundation.data.CreateRegistrate
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
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

@Mod(CwArmsRace.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object CwArmsRace {
    const val ID = "armsrace"

    val LOGGER: Logger = LogManager.getLogger(ID)

    val REGISTRATE = CreateRegistrate.create(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        REGISTRATE.registerEventListeners(MOD_BUS)

        ModCreativeModeTabs.BASE_TAB
        ModItems.FPV_DRONE
        ModEntities.FPV_DRONEq;


        val obj = runForDist(clientTarget = {
            MOD_BUS.addListener(::onClientSetup)
            Minecraft.getInstance()
        }, serverTarget = {
            MOD_BUS.addListener(::onServerSetup)
            "test"
        })

        println(obj)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")

        NeoForge.EVENT_BUS.addListener(CameraHandler::onClientTick)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onRenderHand)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onCalculateTurn)
        NeoForge.EVENT_BUS.addListener(CameraHandler::onFramePre)
    }

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
                drone.reconcile(payload.x, payload.y, payload.z, payload.seq, payload.vx, payload.vy, payload.vz)
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
