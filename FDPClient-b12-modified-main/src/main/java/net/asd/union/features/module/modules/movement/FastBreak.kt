/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.features.module.modules.other.Fucker
import net.asd.union.features.module.modules.other.Nuker
import net.asd.union.config.float
import net.asd.union.event.handler

object FastBreak : Module("FastBreak", Category.MOVEMENT, hideModule = false) {

    private val breakDamage by float("BreakDamage", 0.8F, 0.1F..1F)

    val onUpdate = handler<UpdateEvent> { event ->
        mc.playerController.blockHitDelay = 0

        if (mc.playerController.curBlockDamageMP > breakDamage)
            mc.playerController.curBlockDamageMP = 1F

        if (Fucker.currentDamage > breakDamage)
            Fucker.currentDamage = 1F

        if (Nuker.currentDamage > breakDamage)
            Nuker.currentDamage = 1F
    }
}
