package com.curbs.armsrace

import com.curbs.armsrace.entity.DroneEntity
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.RenderHandEvent

object CameraHandler {
    /** Pilot's predicted look. Server echo never touches this. */
    private var predYaw = 0f
    private var predPitch = 0f
    private var pilotedId: Int? = null

    private fun pilotedDrone(): DroneEntity? {
        val drone = Minecraft.getInstance().cameraEntity as? DroneEntity
            ?: run {
                pilotedId = null
                return null
            }
        if (drone.isRemoved) {
            pilotedId = null
            return null
        }
        if (pilotedId != drone.id) {
            pilotedId = drone.id
            predYaw = drone.yRot
            predPitch = drone.xRot
        }
        return drone
    }
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

    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val mc = Minecraft.getInstance()
        val drone = pilotedDrone() ?: return

        val realSens = event.mouseSensitivity
        val f = realSens * 0.6 + 0.2
        val f8 = f * f * f * 8.0 // vanilla normal-turn scale, matches MouseHandler.turnPlayer

        val dx = mc.mouseHandler.xVelocity * f8
        val dy = mc.mouseHandler.yVelocity * f8
        val inv = if (mc.options.invertYMouse().get()) -1.0 else 1.0

        // Entity.turn() multiplies by 0.15 inside; replicate it here.
        // Absolute set from pred so a server echo landing between frames
        // can't skew the base we accumulate onto.
        predYaw += (dx * 0.15).toFloat()
        predPitch = (predPitch + (dy * inv * 0.15).toFloat()).coerceIn(-90f, 90f)
        drone.setYRot(predYaw)
        drone.setXRot(predPitch)
        drone.yRotO = predYaw
        drone.xRotO = predPitch

        // f = 0 -> player.turn(0, 0); -1/3 * 0.6 + 0.2 = 0
        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false
    }

    /**
     * Runs after entity-sync packets are applied, before the frame renders:
     * reasserts predicted look so server echo never survives a frame.
     */
    fun onFramePre(event: RenderFrameEvent.Pre) {
        val drone = pilotedDrone() ?: return
        drone.setYRot(predYaw)
        drone.setXRot(predPitch)
        drone.yRotO = predYaw
        drone.xRotO = predPitch
    }
}