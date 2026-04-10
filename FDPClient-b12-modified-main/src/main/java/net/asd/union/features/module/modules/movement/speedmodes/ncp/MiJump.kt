/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.ncp

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving

object MiJump : SpeedMode("MiJump") {
    override fun onMotion() {
        if (!mc.thePlayer.isMoving) return
        if (mc.thePlayer.onGround && !mc.thePlayer.movementInput.jump) {
            mc.thePlayer.motionY += 0.1
            val multiplier = 1.8
            mc.thePlayer.motionX *= multiplier
            mc.thePlayer.motionZ *= multiplier
            val currentSpeed = speed
            val maxSpeed = 0.66
            if (currentSpeed > maxSpeed) {
                mc.thePlayer.motionX = mc.thePlayer.motionX / currentSpeed * maxSpeed
                mc.thePlayer.motionZ = mc.thePlayer.motionZ / currentSpeed * maxSpeed
            }
        }
        strafe()
    }

}