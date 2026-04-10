/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.other

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object SlowHop : SpeedMode("SlowHop") {
    override fun onMotion() {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid || player.isInWeb || player.isOnLadder) return

        if (player.isMoving) {
            if (player.onGround) player.tryJump() else speed *= 1.011f
        } else {
            player.motionX = 0.0
            player.motionZ = 0.0
        }
    }

}