/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.other

import net.asd.union.features.module.modules.movement.flymodes.FlyMode

object HawkEye : FlyMode("HawkEye") {
	override fun onUpdate() {
		mc.thePlayer.motionY = if (mc.thePlayer.motionY <= -0.42) 0.42 else -0.42
	}
}
