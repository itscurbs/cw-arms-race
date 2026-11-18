package com.curbs.armsrace.api.uav

import dev.ryanhcode.sable.api.physics.`object`.box.BoxPhysicsObject
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.sqrt

fun interface UavBodyFactory {
    fun create(level: Level, entity: Entity, position: Vec3, velocity: Vec3): UavBody?
}

interface UavBody {
    val active: Boolean

    fun center(): Vec3

    fun velocity(): Vec3

    fun sync(): Double

    fun drive(input: UavControlInput, model: UavFlightModel, spec: UavFlightSpec)

    fun setVelocity(velocity: Vec3)

    fun remove()
}

class SableUavBody private constructor(
    private val level: ServerLevel,
    private val box: BoxPhysicsObject,
    private val handle: RigidBodyHandle,
) : UavBody {
    private val pose = Pose3d()
    private val linear = Vector3d()
    private val angular = Vector3d()
    private val linearDelta = Vector3d()
    private val angularDelta = Vector3d()
    private val commandedVelocity = Vector3d()
    private var hasCommandedVelocity = false

    override val active: Boolean
        get() = box.isActive && handle.isValid()

    override fun sync(): Double {
        if (!box.isActive) return 0.0
        box.updatePose()
        pose.set(box.getPose())
        handle.getLinearVelocity(linear)
        val verticalImpact = if (
            hasCommandedVelocity &&
            commandedVelocity.y < -1.5 &&
            linear.y > commandedVelocity.y * 0.35
        ) {
            -commandedVelocity.y
        } else {
            0.0
        }
        val beforeHorizontal = sqrt(commandedVelocity.x * commandedVelocity.x + commandedVelocity.z * commandedVelocity.z)
        val afterHorizontal = sqrt(linear.x * linear.x + linear.z * linear.z)
        val reversed = commandedVelocity.x * linear.x + commandedVelocity.z * linear.z < 0.0
        val horizontalImpact = if (beforeHorizontal > 7.0 && reversed) {
            beforeHorizontal + afterHorizontal
        } else {
            0.0
        }
        return maxOf(verticalImpact, horizontalImpact)
    }

    override fun center(): Vec3 = Vec3(pose.position().x, pose.position().y, pose.position().z)

    override fun velocity(): Vec3 {
        if (!active) return Vec3.ZERO
        handle.getLinearVelocity(linear)
        return Vec3(linear.x, linear.y, linear.z)
    }

    override fun drive(input: UavControlInput, model: UavFlightModel, spec: UavFlightSpec) {
        if (!active) return
        handle.getLinearVelocity(linear)
        val state = UavFlightState(Vec3(linear.x, linear.y, linear.z))
        val desired = model.step(state, input, spec)
        var dx = desired.x - linear.x
        var dy = desired.y - linear.y + spec.gravityPerTick
        var dz = desired.z - linear.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length > spec.maxDeltaVelocity) {
            val scale = spec.maxDeltaVelocity / length
            dx *= scale
            dy *= scale
            dz *= scale
        }
        linearDelta.set(dx, dy, dz)
        handle.getAngularVelocity(angular)
        angularDelta.set(-angular.x * 0.5, -angular.y * 0.5, -angular.z * 0.5)
        handle.addLinearAndAngularVelocity(linearDelta, angularDelta)
        handle.getLinearVelocity(commandedVelocity)
        hasCommandedVelocity = true
    }

    override fun setVelocity(velocity: Vec3) {
        if (!active) return
        handle.getLinearVelocity(linear)
        linear.set(velocity.x - linear.x, velocity.y - linear.y, velocity.z - linear.z)
        angular.zero()
        handle.addLinearAndAngularVelocity(linear, angular)
    }

    override fun remove() {
        SubLevelPhysicsSystem.get(level)?.removeObject(box)
    }

    companion object {
        fun factory(
            width: Double,
            height: Double,
            depth: Double,
            mass: Double,
        ): UavBodyFactory = UavBodyFactory { level, _, position, velocity ->
            val serverLevel = level as? ServerLevel ?: return@UavBodyFactory null
            val system = SubLevelPhysicsSystem.get(serverLevel) ?: return@UavBodyFactory null
            val pose = Pose3d()
            pose.position().set(position.x, position.y, position.z)
            val box = BoxPhysicsObject(
                pose,
                Vector3d(width * 0.5, height * 0.5, depth * 0.5),
                mass,
            )
            system.addObject(box)
            val handle = RigidBodyHandle.of(serverLevel, box) ?: run {
                system.removeObject(box)
                return@UavBodyFactory null
            }
            SableUavBody(serverLevel, box, handle).also { it.setVelocity(velocity) }
        }
    }
}

class KinematicUavBody private constructor(
    private val entity: Entity,
) : UavBody {
    private var velocity: Vec3 = Vec3.ZERO

    override val active: Boolean = true

    override fun center(): Vec3 =
        entity.position().add(0.0, entity.boundingBox.ysize * 0.5, 0.0)

    override fun velocity(): Vec3 = velocity

    override fun sync(): Double = 0.0

    override fun drive(input: UavControlInput, model: UavFlightModel, spec: UavFlightSpec) {
        velocity = UavKinematics.move(entity, velocity, input, model, spec)
    }

    override fun setVelocity(velocity: Vec3) {
        this.velocity = velocity
    }

    override fun remove() {
    }

    companion object {
        val FACTORY: UavBodyFactory = UavBodyFactory { _, entity, _, velocity ->
            KinematicUavBody(entity).also { it.setVelocity(velocity) }
        }
    }
}

object UavKinematics {
    fun move(
        entity: Entity,
        velocity: Vec3,
        input: UavControlInput,
        model: UavFlightModel,
        spec: UavFlightSpec,
        restitution: Double = 0.15,
    ): Vec3 {
        var next = model.step(UavFlightState(velocity), input, spec)
        val tickVelocity = next.scale(1.0 / 20.0)
        val steps = 4
        val step = tickVelocity.scale(1.0 / steps)
        val grounded = !entity.level().noCollision(entity, entity.boundingBox.move(0.0, -0.03, 0.0))
        if (grounded && next.y < 0.0) {
            next = Vec3(next.x, 0.0, next.z)
        }
        repeat(steps) {
            if (entity.level().noCollision(entity, entity.boundingBox.move(step.x, 0.0, 0.0))) {
                entity.setPos(entity.x + step.x, entity.y, entity.z)
            } else {
                next = Vec3(-next.x * restitution, next.y, next.z)
            }
            if (grounded && step.y <= 0.0) {
                next = Vec3(next.x, 0.0, next.z)
            } else if (entity.level().noCollision(entity, entity.boundingBox.move(0.0, step.y, 0.0))) {
                entity.setPos(entity.x, entity.y + step.y, entity.z)
            } else {
                next = Vec3(next.x, -next.y * restitution, next.z)
            }
            if (entity.level().noCollision(entity, entity.boundingBox.move(0.0, 0.0, step.z))) {
                entity.setPos(entity.x, entity.y, entity.z + step.z)
            } else {
                next = Vec3(next.x, next.y, -next.z * restitution)
            }
        }
        if (next.y <= 0.0 && !entity.level().noCollision(entity, entity.boundingBox.move(0.0, -0.02, 0.0))) {
            next = Vec3(next.x, 0.0, next.z)
        }
        return next
    }
}
