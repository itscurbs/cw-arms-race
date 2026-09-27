package com.curbs.cw_arms_race

import com.curbs.cw_arms_race.client.DroneRenderer
import com.curbs.cw_arms_race.entity.ModEntities
import com.curbs.cw_arms_race.item.ModItems
import net.minecraft.client.Minecraft
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
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
@Mod(Cw_arms_race.ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Cw_arms_race {
    const val ID = "cw_arms_race"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        ModItems.REGISTRY.register(MOD_BUS)
        ModEntities.REGISTRY.register(MOD_BUS)


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
    fun addCreative(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.FPV_DRONE)
        }
    }
}
