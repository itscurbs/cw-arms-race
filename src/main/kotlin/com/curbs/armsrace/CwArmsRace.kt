package com.curbs.armsrace

import com.curbs.armsrace.api.uav.UavCameraPayload
import com.curbs.armsrace.api.uav.UavControlInput
import com.curbs.armsrace.api.uav.UavInputPayload
import com.curbs.armsrace.api.uav.UavInputReceiver
import com.curbs.armsrace.api.uav.UavPilot
import com.curbs.armsrace.api.uav.UavReconcilable
import com.curbs.armsrace.api.uav.UavStatePayload
import com.curbs.armsrace.block.ModBlockEntities
import com.curbs.armsrace.block.ModBlocks
import com.curbs.armsrace.client.KilnRenderer
import com.curbs.armsrace.client.WeaverRenderer
import com.curbs.armsrace.fluid.ModFluids
import com.curbs.armsrace.item.ModCreativeModeTabs
import com.curbs.armsrace.item.ModDataComponents
import com.curbs.armsrace.item.ModItems
import com.curbs.armsrace.recipe.ModRecipes
import com.curbs.armsrace.uav.FpvDrone
import com.simibubi.create.foundation.data.CreateRegistrate
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
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

    val REGISTRATE: CreateRegistrate = CreateRegistrate.create(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        REGISTRATE.registerEventListeners(MOD_BUS)

        FpvDrone.TYPE
        ModCreativeModeTabs.BASE_TAB
        ModDataComponents.DRONE_LINK
        ModItems.CONTROLLER
        ModFluids.EPOXY
        ModBlocks.KILN
        ModBlocks.WEAVER
        ModBlockEntities.KILN
        ModBlockEntities.WEAVER
        ModRecipes.register()

        val obj =
            runForDist(clientTarget = {
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
        event.enqueueWork {
            BlockEntityRenderers.register(ModBlockEntities.KILN.get(), ::KilnRenderer)
            BlockEntityRenderers.register(ModBlockEntities.WEAVER.get(), ::WeaverRenderer)
        }

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
    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.KILN.get(),
        ) { be, _ -> be.capability }

        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.WEAVER.get(),
        ) { be, _ -> be.capability }
    }

    @SubscribeEvent
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.playToServer(
            UavInputPayload.TYPE,
            UavInputPayload.STREAM_CODEC,
        ) { payload, context ->
            context.enqueueWork {
                val player = context.player()
                val data = player.persistentData
                if (!data.hasUUID("Drone")) return@enqueueWork
                val entity = (player.level() as? ServerLevel)?.getEntity(data.getUUID("Drone"))
                    ?: return@enqueueWork
                val receiver = entity as? UavInputReceiver ?: return@enqueueWork
                receiver.queueInput(
                    UavControlInput(
                        payload.seq,
                        payload.move,
                        payload.yaw,
                        payload.pitch.coerceIn(-90f, 90f),
                        payload.throttle,
                        payload.action,
                    )
                )
            }
        }

        registrar.playToClient(
            UavStatePayload.TYPE,
            UavStatePayload.STREAM_CODEC,
        ) { payload, context ->
            context.enqueueWork {
                val entity = Minecraft.getInstance().level?.getEntity(payload.uavId)
                    ?: return@enqueueWork
                val reconcilable = entity as? UavReconcilable ?: return@enqueueWork
                reconcilable.reconcile(
                    Vec3(payload.x, payload.y, payload.z),
                    Vec3(payload.vx, payload.vy, payload.vz),
                    payload.seq,
                    payload.confirmedImpact,
                )
            }
        }

        registrar.playToClient(
            UavCameraPayload.TYPE,
            UavCameraPayload.STREAM_CODEC,
        ) { payload, context ->
            context.enqueueWork {
                val entity = Minecraft.getInstance().level?.getEntity(payload.uavId)
                    ?: return@enqueueWork
                if (entity is UavPilot) {
                    Minecraft.getInstance().cameraEntity = entity
                }
            }
        }
    }
}
