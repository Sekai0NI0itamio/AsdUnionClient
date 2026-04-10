/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.ncp

import net.asd.union.event.PacketEvent
import net.asd.union.features.module.modules.movement.Flight.ncpMotion
import net.asd.union.features.module.modules.movement.flymodes.FlyMode
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.component1
import net.asd.union.utils.extensions.component2
import net.asd.union.utils.extensions.component3
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

object NCP : FlyMode("NCP") {
	override fun onEnable() {
		if (!mc.thePlayer.onGround) return

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
		mc.thePlayer.motionY =
			if (mc.gameSettings.keyBindSneak.isKeyDown) -0.5
			else -ncpMotion.toDouble()

		strafe()
	}

	override fun onPacket(event: PacketEvent) {
		val packet = event.packet

		if (packet is C03PacketPlayer)
			packet.onGround = true
	}

}
