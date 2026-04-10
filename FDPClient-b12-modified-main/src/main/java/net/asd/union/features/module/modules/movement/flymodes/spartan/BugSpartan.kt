/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.spartan

import net.asd.union.features.module.modules.movement.Flight.vanillaSpeed
import net.asd.union.features.module.modules.movement.flymodes.FlyMode
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.component1
import net.asd.union.utils.extensions.component2
import net.asd.union.utils.extensions.component3
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

object BugSpartan : FlyMode("BugSpartan") {
	override fun onEnable() {
		val (x, y, z) = mc.thePlayer

		repeat(65) {
			sendPackets(
				C04PacketPlayerPosition(x, y + 0.049, z, false),
				C04PacketPlayerPosition(x, y, z, false)
			)
		}

		sendPacket(C04PacketPlayerPosition(x, y + 0.1, z, true))

		mc.thePlayer.motionX *= 0.1
		mc.thePlayer.motionZ *= 0.1
		mc.thePlayer.swingItem()
	}

	override fun onUpdate() {
		mc.thePlayer.capabilities.isFlying = false

		mc.thePlayer.motionY = when {
			mc.gameSettings.keyBindJump.isKeyDown -> vanillaSpeed.toDouble()
			mc.gameSettings.keyBindSneak.isKeyDown -> -vanillaSpeed.toDouble()
			else -> 0.0
		}

		strafe(vanillaSpeed, true)
	}
}
