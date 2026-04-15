package net.asd.union.features.module.modules.combat

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.EntityFishHook
import net.minecraft.util.DamageSource

object RodVelocity : Module("RodVelocity", Category.COMBAT, hideModule = false) {

    private val support = SourceVelocitySupport(
        this,
        "RodTimeout",
        { entity -> entity is EntityFishHook }
    )

    override val tag
        get() = support.tag

    override fun onEnable() {
        support.onEnable()
    }

    override fun onDisable() {
        support.onDisable()
    }

    fun shouldCancelKnockback() = support.shouldCancelKnockback()

    fun markKnockbackBlock() = support.markKnockbackBlock()

    fun isKnockbackBlockArmed(): Boolean = support.isKnockbackBlockArmed()

    fun consumeKnockbackBlock(): Boolean = support.consumeKnockbackBlock()

    fun onMinecraftDamageSource(source: DamageSource, directSource: Entity?, indirectSource: Entity?): Boolean {
        return support.onMinecraftDamageSource(source, directSource, indirectSource)
    }

    fun shouldBlockIncomingVelocity(stage: String, motionX: Double, motionY: Double, motionZ: Double): Boolean {
        return support.shouldBlockIncomingVelocity(stage, motionX, motionY, motionZ)
    }

    fun onRodHit() {
        support.onSourceHit()
    }
}