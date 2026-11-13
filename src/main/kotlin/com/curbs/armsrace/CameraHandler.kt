package com.curbs.armsrace

import com.curbs.armsrace.api.uav.UavPilot
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

    private fun pilotedUav(): UavPilot? {
        val drone = Minecraft.getInstance().cameraEntity as? UavPilot
            ?: run {
                pilotedId = null
                return null
            }
        if (drone.pilotEntity.isRemoved) {
            pilotedId = null
            return null
        }
        if (pilotedId != drone.pilotEntity.id) {
            pilotedId = drone.pilotEntity.id
            predYaw = drone.pilotEntity.yRot
            predPitch = drone.pilotEntity.xRot
        }
        return drone
    }
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val cam = mc.cameraEntity

        if (cam is UavPilot && cam.predictedCollision) {
            cam.predictedCollision = false
            mc.cameraEntity = mc.player
            return
        }
        if (cam is UavPilot && cam.pilotEntity.isRemoved) {
            mc.cameraEntity = mc.player
        }
    }

    fun onRenderHand(event: RenderHandEvent) {
        val mc = Minecraft.getInstance()

        if (mc.cameraEntity is UavPilot) {
            event.isCanceled = true
        }
    }

    fun onCalculateTurn(event: CalculatePlayerTurnEvent) {
        val mc = Minecraft.getInstance()
        val drone = pilotedUav() ?: return

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
        drone.applyPilotLook(predYaw, predPitch)

        // f = 0 -> player.turn(0, 0); -1/3 * 0.6 + 0.2 = 0
        event.mouseSensitivity = -1.0 / 3.0
        event.cinematicCameraEnabled = false
    }

    /**
     * Runs after entity-sync packets are applied, before the frame renders:
     * reasserts predicted look so server echo never survives a frame.
     */
    fun onFramePre(event: RenderFrameEvent.Pre) {
        val drone = pilotedUav() ?: return
        drone.applyPilotLook(predYaw, predPitch)
    }
}