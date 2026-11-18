package com.curbs.armsrace.api.uav

import com.curbs.armsrace.CwArmsRace
import com.curbs.armsrace.api.uav.client.UavRenderer
import com.curbs.armsrace.api.uav.client.UavStationBlockItem
import com.curbs.armsrace.api.uav.client.UavStationRenderer
import com.tterrag.registrate.util.nullness.NonNullFunction
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.client.model.generators.ModelFile

object UavRegistration {
    fun register(name: String, spec: UavSpec, station: Boolean = true): UavType {
        val uavType = registerEntity(name, spec)
        if (station) registerStation(uavType)
        return uavType
    }

    fun registerEntity(name: String, spec: UavSpec): UavType {
        val entityEntry = CwArmsRace.REGISTRATE
            .entity(name, { type, level -> UavEntity(type, level, spec) }, MobCategory.MISC)
            .properties { builder ->
                builder.sized(spec.width, spec.height)
                builder.clientTrackingRange(96)
            }
            .renderer {
                NonNullFunction<EntityRendererProvider.Context, EntityRenderer<in UavEntity>> { context ->
                    UavRenderer(context)
                }
            }
            .lang(spec.displayName)
            .register()

        return UavType(name, spec, entityEntry)
    }

    fun registerStation(uavType: UavType): UavType {
        val name = uavType.name
        val spec = uavType.spec

        uavType.block = CwArmsRace.REGISTRATE
            .block(name, { properties -> GenericUavStationBlock(properties, uavType) })
            .initialProperties { Blocks.IRON_BLOCK }
            .properties { it.noOcclusion() }
            .blockstate { context, provider ->
                val model = provider.models()
                    .getBuilder(name)
                    .texture("particle", provider.mcLoc("block/stone"))
                provider.simpleBlock(context.getEntry(), model)
            }
            .item { block, properties -> UavStationBlockItem(block, properties, uavType) }
            .model { context, provider ->
                provider.getBuilder(context.name)
                    .parent(ModelFile.UncheckedModelFile(provider.mcLoc("builtin/entity")))
            }
            .build()
            .lang(spec.displayName)
            .register()

        uavType.blockEntity = CwArmsRace.REGISTRATE
            .blockEntity(name) { type, pos, state ->
                GenericUavStationBlockEntity(type, pos, state, uavType)
            }
            .validBlocks(uavType.block ?: error("Station block not registered"))
            .renderer {
                NonNullFunction<BlockEntityRendererProvider.Context, BlockEntityRenderer<in GenericUavStationBlockEntity>> { context ->
                    UavStationRenderer(context)
                }
            }
            .register()

        return uavType
    }
}
