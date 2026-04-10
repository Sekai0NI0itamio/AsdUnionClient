/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.nowebmodes.intave

import net.asd.union.features.module.modules.movement.nowebmodes.NoWebMode

object IntaveOld : NoWebMode("IntaveOld") {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (!thePlayer.isInWeb) {
            return
        }

        if (thePlayer.movementInput.moveStrafe == 0.0F && mc.gameSettings.keyBindForward.isKeyDown && thePlayer.isCollidedVertically) {
            thePlayer.jumpMovementFactor = 0.74F
        } else {
            thePlayer.jumpMovementFactor = 0.2F
            thePlayer.onGround = true
        }  
    }
}
