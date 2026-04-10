/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.nowebmodes.other

import net.asd.union.features.module.modules.movement.nowebmodes.NoWebMode
import net.asd.union.utils.extensions.tryJump

object Rewi : NoWebMode("Rewi") {
    override fun onUpdate() {
        if (!mc.thePlayer.isInWeb) {
            return
        }
        mc.thePlayer.jumpMovementFactor = 0.42f

        if (mc.thePlayer.onGround)
            mc.thePlayer.tryJump()
    }
}
