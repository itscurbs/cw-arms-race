package com.curbs.armsrace.fluid

import com.curbs.armsrace.CwArmsRace
import com.tterrag.registrate.util.entry.FluidEntry
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.fluids.BaseFlowingFluid

object ModFluids {
    val EPOXY: FluidEntry<BaseFlowingFluid.Flowing> = CwArmsRace.REGISTRATE
        .fluid(
            "epoxy",
            ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "fluid/epoxy_still"),
            ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "fluid/epoxy_flow")
        )
        .lang("Epoxy")
        .properties { properties -> properties.viscosity(2000).density(1100) }
        .noBucket()
        .register()
}
