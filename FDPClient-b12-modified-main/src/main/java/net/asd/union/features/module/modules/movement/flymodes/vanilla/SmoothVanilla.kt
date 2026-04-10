/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.vanilla

import net.asd.union.features.module.modules.movement.Flight.handleVanillaKickBypass
import net.asd.union.features.module.modules.movement.flymodes.FlyMode

object SmoothVanilla : FlyMode("SmoothVanilla") {
	override fun onUpdate() {
		mc.thePlayer.capabilities.isFlying = true
		handleVanillaKickBypass()
	}
}
