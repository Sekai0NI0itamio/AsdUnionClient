package net.asd.union.features.module.modules.player.nofallmodes.aac

import net.asd.union.features.module.modules.player.nofallmodes.NoFallMode
import net.asd.union.utils.movement.MovementUtils.serverOnGround
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.stopXZ
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

object AAC3311 : NoFallMode("AAC3.3.11") {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer

        if (thePlayer.fallDistance > 2) {
            thePlayer.stopXZ()

            sendPackets(
                C04PacketPlayerPosition(thePlayer.posX, thePlayer.posY - 10E-4, thePlayer.posZ, serverOnGround),
                C03PacketPlayer(true)
            )
        }
    }
}