package com.curbs.cw_arms_race.client

import com.curbs.cw_arms_race.entity.DroneEntity
import com.curbs.cw_arms_race.entity.DroneGeoModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/** Client-only renderer for the drone. */
class DroneRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<DroneEntity>(context, DroneGeoModel())
