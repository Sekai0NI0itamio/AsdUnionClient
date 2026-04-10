/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.ncp

import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object NCPFHop : SpeedMode("NCPFHop") {
    override fun onEnable() {
        mc.timer.timerSpeed = 1.0866f
        super.onEnable()
    }

    override fun onDisable() {
        mc.thePlayer.speedInAir = 0.02f
        mc.timer.timerSpeed = 1f
        super.onDisable()
    }

    override fun onUpdate() {
        if (mc.thePlayer.isMoving) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.tryJump()
                mc.thePlayer.motionX *= 1.01
                mc.thePlayer.motionZ *= 1.01
                mc.thePlayer.speedInAir = 0.0223f
            }
            mc.thePlayer.motionY -= 0.00099999
            strafe()
        } else {
            mc.thePlayer.motionX = 0.0
            mc.thePlayer.motionZ = 0.0
        }
    }

}