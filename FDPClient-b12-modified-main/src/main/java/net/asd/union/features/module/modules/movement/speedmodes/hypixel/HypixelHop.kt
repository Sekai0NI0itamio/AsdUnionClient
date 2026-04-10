/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.hypixel

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object HypixelHop : SpeedMode("HypixelHop") {
    override fun onStrafe() {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid)
            return

        if (player.onGround && player.isMoving) {
            if (player.isUsingItem) {
                player.tryJump()
            } else {
                player.tryJump()
                strafe(0.48f)
            }
        }

    }
}
