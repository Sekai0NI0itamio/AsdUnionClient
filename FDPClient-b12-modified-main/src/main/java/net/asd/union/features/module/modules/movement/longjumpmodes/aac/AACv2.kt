/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.longjumpmodes.aac

import net.asd.union.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.asd.union.utils.movement.MovementUtils

object AACv2 : LongJumpMode("AACv2") {
    override fun onUpdate() {
        mc.thePlayer.jumpMovementFactor = 0.09f
        mc.thePlayer.motionY += 0.01320999999999999
        mc.thePlayer.jumpMovementFactor = 0.08f
        MovementUtils.strafe()
    }
}