package com.curbs.cw_arms_race

import com.julian.createwarfare.effects.server.SmokeEffect
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object WarfareTest {
    fun register() = NeoForge.EVENT_BUS.register(this)

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val level = event.level
        if (level !is ServerLevel) return
        SmokeEffect.start(
            level, event.pos,
            3.0f, // radius
            100, // duration ticks
            20.0f, // density
            0.5f, // riseSpeed
            60 // particle lifetime
        )
    }
}
