package com.curbs.armsrace.fluid

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.builders.FluidBuilder
import com.tterrag.registrate.util.entry.FluidEntry
import com.mojang.blaze3d.shaders.FogShape
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.fluids.FluidType
import org.joml.Vector3f

object ModFluids {
    val EPOXY: FluidEntry<BaseFlowingFluid.Flowing> = CwArmsRace.REGISTRATE
        .fluid(
            "epoxy",
            ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "block/epoxy_still"),
            ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "block/epoxy_flow"),
            FluidBuilder.FluidTypeFactory { properties, stillTexture, flowingTexture ->
                object : FluidType(properties) {
                    override fun initializeClient(consumer: java.util.function.Consumer<IClientFluidTypeExtensions>) {
                        consumer.accept(object : IClientFluidTypeExtensions {
                            override fun getStillTexture(): ResourceLocation = stillTexture

                            override fun getFlowingTexture(): ResourceLocation = flowingTexture

                            override fun modifyFogColor(
                                camera: Camera,
                                partialTick: Float,
                                level: ClientLevel,
                                renderDistance: Int,
                                darkenWorldAmount: Float,
                                fluidFogColor: Vector3f
                            ): Vector3f = Vector3f(0.55f, 0.3f, 0.12f)

                            override fun modifyFogRender(
                                camera: Camera,
                                mode: FogRenderer.FogMode,
                                renderDistance: Float,
                                partialTick: Float,
                                nearDistance: Float,
                                farDistance: Float,
                                shape: FogShape
                            ) {
                                RenderSystem.setShaderFogStart(0.25f)
                                RenderSystem.setShaderFogEnd(4.0f)
                                RenderSystem.setShaderFogShape(FogShape.SPHERE)
                            }
                        })
                    }
                }
            }
        )
        .lang("Epoxy")
        .properties { properties -> properties.viscosity(2000).density(1100) }
        .renderType(java.util.function.Supplier { java.util.function.Supplier { RenderType.translucent() } })
        .noBucket()
        .register()
}
