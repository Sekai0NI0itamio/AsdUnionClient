/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.float

object KeepSprint : Module("KeepSprint", Category.COMBAT, hideModule = false) {
    private val motionAfterAttackOnGround by float("MotionAfterAttackOnGround", 0.6f, 0.0f..1f)
    private val motionAfterAttackInAir by float("MotionAfterAttackInAir", 0.6f, 0.0f..1f)

    val motionAfterAttack
        get() = if (mc.thePlayer.onGround) motionAfterAttackOnGround else motionAfterAttackInAir
}