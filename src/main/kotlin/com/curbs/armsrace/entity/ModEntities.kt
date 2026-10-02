package com.curbs.armsrace.entity

import com.curbs.armsrace.CwArmsRace
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModEntities {

    // There is no createEntities() helper - entities use the generic create().
    val REGISTRY: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CwArmsRace.ID)

    val FPV_DRONE by REGISTRY.register("fpv_drone") { ->
        EntityType.Builder
            // Factory: how the game creates a new instance.
            .of({ type, level -> DroneEntity(type, level) }, MobCategory.MISC)

            // Hitbox. Order is width, then height. Minecraft hitboxes are 2D,
            // so there is no separate depth - 1.0 wide x 0.5 tall is a flat drone.
            .sized(1.0f, 0.5f)

            // Vanilla default is 5 blocks, too short to see a drone flying away.
            .clientTrackingRange(64)

            // The string here only feeds the data fixer; the real registry name
            // comes from the "fpv_drone" passed to register() above.
            .build("armsrace:fpv_drone")
    }
}
