/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.nowebmodes.intave

import net.asd.union.features.module.modules.movement.nowebmodes.NoWebMode
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object IntaveNew : NoWebMode("IntaveNew") {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (!thePlayer.isInWeb) {
            return
        }

        if (thePlayer.isMoving && thePlayer.moveStrafing == 0.0f) {
            if (thePlayer.onGround) {
                if (mc.thePlayer.ticksExisted % 3 == 0) {
                    strafe(0.734f)
                } else {
                    mc.thePlayer.tryJump()
                    strafe(0.346f)
                }
            }
        }
    }
}
