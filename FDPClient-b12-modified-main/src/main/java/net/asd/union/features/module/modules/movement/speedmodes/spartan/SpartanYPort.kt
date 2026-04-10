/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.spartan

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.tryJump
import net.asd.union.utils.kotlin.RandomUtils.nextDouble

object SpartanYPort : SpeedMode("SpartanYPort") {
    private var airMoves = 0
    override fun onMotion() {
        if (mc.gameSettings.keyBindForward.isKeyDown) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.tryJump()
                airMoves = 0
            } else {
                mc.timer.timerSpeed = 1.08f
                if (airMoves >= 3) mc.thePlayer.jumpMovementFactor = 0.0275f
                if (airMoves >= 4 && airMoves % 2 == 0) {
                    mc.thePlayer.motionY = -0.32 - nextDouble(endInclusive = 0.009)
                    mc.thePlayer.jumpMovementFactor = 0.0238f
                }
                airMoves++
            }
        }
    }

}