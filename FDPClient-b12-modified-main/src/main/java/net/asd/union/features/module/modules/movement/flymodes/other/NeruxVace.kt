/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.other

import net.asd.union.features.module.modules.movement.Flight.neruxVaceTicks
import net.asd.union.features.module.modules.movement.flymodes.FlyMode

object NeruxVace : FlyMode("NeruxVace") {
	private var tick = 0
	override fun onUpdate() {
		if (!mc.thePlayer.onGround)
			tick++

		if (tick >= neruxVaceTicks && !mc.thePlayer.onGround) {
			tick = 0
			mc.thePlayer.motionY = .015
		}
	}
}
