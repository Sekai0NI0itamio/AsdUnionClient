/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.vulcan

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object VulcanHop : SpeedMode("VulcanHop") {
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid || player.isInWeb || player.isOnLadder) return

        if (player.isMoving) {
            if (player.isAirBorne && player.fallDistance > 2) {
                mc.timer.timerSpeed = 1f
                return
            }

            if (player.onGround) {
                player.tryJump()
                if (player.motionY > 0) {
                    mc.timer.timerSpeed = 1.1453f
                }
                strafe(0.4815f)
            } else {
                if (player.motionY < 0) {
                    mc.timer.timerSpeed = 0.9185f
                }
            }
        } else {
            mc.timer.timerSpeed = 1f
        }
    }
}