package net.asd.union.features.module.modules.combat

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityEnderPearl
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityEgg
import net.minecraft.entity.projectile.EntityPotion
import net.minecraft.entity.projectile.EntitySnowball
import net.minecraft.util.DamageSource

object ProjectileVelocity : Module("ProjectileVelocity", Category.COMBAT, hideModule = false) {

    private val support = SourceVelocitySupport(
        this,
        "ProjectileTimeout",
        { entity ->
            entity is EntityArrow ||
                entity is EntitySnowball ||
                entity is EntityEgg ||
                entity is EntityPotion ||
                entity is EntityEnderPearl
        }
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

    fun onProjectileHit() {
        support.onSourceHit()
    }
}