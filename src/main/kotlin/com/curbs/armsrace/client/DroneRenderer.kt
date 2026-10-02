package com.curbs.armsrace.client

import com.curbs.armsrace.entity.DroneEntity
import com.curbs.armsrace.entity.DroneGeoModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/** Client-only renderer for the drone. */
class DroneRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<DroneEntity>(context, DroneGeoModel())
