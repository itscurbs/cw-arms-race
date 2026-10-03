package com.curbs.armsrace.entity

import com.curbs.armsrace.CwArmsRace
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.animation.AnimationState
import software.bernie.geckolib.model.GeoModel

/**
 * Points Geckolib at our .geo.json and texture.
 *
 * The paths are built from ResourceLocation.of(CwArmsRace.ID, ...), so both
 * the namespace and the "fpv_drone" filename are pulled from the mod id and the
 * registry name - no chance of the two drifting apart.
 */
class DroneGeoModel : GeoModel<DroneEntity>() {

    override fun getModelResource(animatable: DroneEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")

    // We have no .animations.json, so point at the model file. Geckolib only
    // reads it if an animation is actually requested.
    override fun getAnimationResource(animatable: DroneEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "geo/fpv_drone.geo.json")

    override fun getTextureResource(animatable: DroneEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CwArmsRace.ID, "textures/entity/fpv_drone.png")

    /**
     * Generic-Entity GeoRenderer passes yaw=0 to applyRotations (it only yaws
     * LivingEntity via yBodyRot), so yaw must be applied to the bone manually,
     * same as pitch. Root ends at 180deg, so bone uses -yRot for 180-yRot total.
     * Flip signs if mirrored/backwards.
     */
    override fun setCustomAnimations(
        animatable: DroneEntity,
        instanceId: Long,
        animationState: AnimationState<DroneEntity>
    ) {
        getAnimationProcessor().getBone("bb_main")?.let { bone ->
            animatable.updateVisualTilt()
            bone.rotX = -(animatable.xRot * animatable.visualPitchFollow + animatable.visualPitchLean) * Math.PI.toFloat() / 180f
            bone.rotZ = animatable.visualRoll * Math.PI.toFloat() / 180f
            bone.rotY = 0f
        }
    }
}
