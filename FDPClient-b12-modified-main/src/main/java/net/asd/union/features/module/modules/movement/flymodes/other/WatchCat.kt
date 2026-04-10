/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.other

import net.asd.union.features.module.modules.movement.Flight.startY
import net.asd.union.features.module.modules.movement.flymodes.FlyMode
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.stopXZ
import net.asd.union.utils.kotlin.RandomUtils.nextDouble

object WatchCat : FlyMode("WatchCat") {
	override fun onUpdate() {
		strafe(0.15f)
		mc.thePlayer.isSprinting = true

		if (mc.thePlayer.posY < startY + 2) {
			mc.thePlayer.motionY = nextDouble(endInclusive = 0.5)
			return
		}

		if (startY > mc.thePlayer.posY) mc.thePlayer.stopXZ()
	}
}
