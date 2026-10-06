package com.curbs.armsrace.entity

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.client.DroneRenderer
import com.tterrag.registrate.util.entry.EntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.MobCategory

object ModEntities {
    val FPV_DRONE: EntityEntry<DroneEntity> = CwArmsRace.REGISTRATE
        .entity("fpv_drone", { type, level -> DroneEntity(type, level) }, MobCategory.MISC)
        .properties { b ->
            b.sized(1.0f, 0.5f)
            b.clientTrackingRange(64)
        }
        .renderer {
            NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in DroneEntity>> { ctx ->
                DroneRenderer(ctx)
            }
        }
        .lang("FPV Drone")
        .register()
}
