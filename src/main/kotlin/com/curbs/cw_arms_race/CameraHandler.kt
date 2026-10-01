package com.curbs.cw_arms_race

import com.curbs.cw_arms_race.entity.DroneEntity
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderHandEvent

object CameraHandler {
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val cam = mc.cameraEntity

        if (cam is DroneEntity && cam.isRemoved) {
            mc.cameraEntity = mc.player
        }
    }

    fun onRenderHand(event: RenderHandEvent) {
        val mc = Minecraft.getInstance()

        if (mc.cameraEntity is DroneEntity) {
            event.isCanceled = true
        }
    }
}