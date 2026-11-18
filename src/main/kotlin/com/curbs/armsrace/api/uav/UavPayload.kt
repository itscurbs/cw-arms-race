package com.curbs.armsrace.api.uav

import com.julian.createwarfare.effects.server.particles.GenericExplosionParticleEffect
import com.julian.createwarfare.explosions.types.GenericExplosion
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.PrimedTnt

fun interface UavPayloadFactory {
    fun create(): UavPayload
}

interface UavPayload {
    fun tick(uav: UavEntity)

    fun onImpact(uav: UavEntity, speed: Double): Boolean

    fun onTrigger(uav: UavEntity): Boolean = false

    fun save(tag: CompoundTag) {}

    fun load(tag: CompoundTag) {}
}

data class UavExplosionSpec(
    val radius: Float = 128.0f,
    val damage: Float = 12.0f,
    val power: Float = 96.0f,
    val triggerSpeed: Double = 6.0,
    val soundVolume: Float = 4.0f,
    val soundPitch: Float = 0.8f,
)

class ExplosiveWarhead(
    private val spec: UavExplosionSpec = UavExplosionSpec(),
) : UavPayload {
    override fun tick(uav: UavEntity) {
    }

    override fun onImpact(uav: UavEntity, speed: Double): Boolean {
        if (speed < spec.triggerSpeed) return false
        detonate(uav)
        return true
    }

    override fun onTrigger(uav: UavEntity): Boolean {
        detonate(uav)
        return true
    }

    private fun detonate(uav: UavEntity) {
        if (uav.isRemoved) return
        val level = uav.level()
        val pos = uav.blockPosition()
        uav.discard()
        val serverLevel = level as? ServerLevel ?: return
        GenericExplosion.trigger(serverLevel, pos, spec.radius, spec.damage, spec.power)
        GenericExplosionParticleEffect.start(serverLevel, pos, spec.radius)
        serverLevel.playSound(
            null,
            pos,
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.BLOCKS,
            spec.soundVolume,
            spec.soundPitch,
        )
    }
}

class BombBay(
    private var bombs: Int = 4,
    private val fuse: Int = 40,
    private val dropOffset: Double = 1.0,
    private val impactSpeed: Double = 8.0,
) : UavPayload {
    override fun tick(uav: UavEntity) {
    }

    override fun onImpact(uav: UavEntity, speed: Double): Boolean =
        speed >= impactSpeed

    override fun onTrigger(uav: UavEntity): Boolean {
        if (bombs <= 0) return false
        val level = uav.level() as? ServerLevel ?: return false
        val bomb = PrimedTnt(level, uav.x, uav.y - dropOffset, uav.z, null)
        bomb.fuse = fuse
        level.addFreshEntity(bomb)
        bombs--
        return false
    }

    override fun save(tag: CompoundTag) {
        tag.putInt("Bombs", bombs)
    }

    override fun load(tag: CompoundTag) {
        bombs = tag.getInt("Bombs")
    }
}
