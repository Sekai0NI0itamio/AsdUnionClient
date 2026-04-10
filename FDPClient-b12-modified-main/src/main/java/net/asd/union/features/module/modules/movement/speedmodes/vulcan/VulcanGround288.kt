/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.vulcan

import net.asd.union.event.JumpEvent
import net.asd.union.event.PacketEvent
import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.potion.Potion

object VulcanGround288 : SpeedMode("VulcanGround288") {
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid || player.isInWeb || player.isOnLadder) return

        if (player.isMoving && collidesBottom()) {
            val speedEffect = player.getActivePotionEffect(Potion.moveSpeed)
            val isAffectedBySpeed = speedEffect != null && speedEffect.amplifier > 0
            val isMovingSideways = player.moveStrafing != 0f

            val strafe = when {
                isAffectedBySpeed -> 0.59f
                isMovingSideways -> 0.41f
                else -> 0.42f
            }

            strafe(strafe)
            player.motionY = 0.005
        }
    }

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (packet is C03PacketPlayer && collidesBottom()) {
            packet.y += 0.005
        }
    }

    private fun collidesBottom(): Boolean {
        val world = mc.theWorld ?: return false
        val player = mc.thePlayer ?: return false

        return world.getCollidingBoundingBoxes(player, player.entityBoundingBox.offset(0.0, -0.005, 0.0)).isNotEmpty()
    }

    override fun onJump(event: JumpEvent) {
        event.cancelEvent()
    }
}