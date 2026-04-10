/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.matrix

import net.asd.union.features.module.modules.movement.Speed
import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.features.module.modules.player.scaffolds.Scaffold
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving

object MatrixHop : SpeedMode("MatrixHop") {

    override fun onUpdate()  {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid || player.isInWeb || player.isOnLadder) return

        if (Speed.matrixLowHop) player.jumpMovementFactor = 0.026f

        if (player.isMoving) {
            if (player.onGround) {
                strafe(if (!Scaffold.handleEvents()) speed + Speed.extraGroundBoost else speed)
                player.motionY = 0.42 - if (Speed.matrixLowHop) 3.48E-3 else 0.0
            } else {
                if (!Scaffold.handleEvents() && speed < 0.19) {
                    strafe()
                }
            }

            if (player.fallDistance <= 0.4 && player.moveStrafing == 0f) {
                player.speedInAir = 0.02035f
            } else {
                player.speedInAir = 0.02f
            }
        }
    }
}