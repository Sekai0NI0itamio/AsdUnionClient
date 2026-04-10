package net.asd.union.features.module.modules.player.nofallmodes.other

import net.asd.union.features.module.modules.player.nofallmodes.NoFallMode
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.minecraft.network.play.client.C03PacketPlayer

object Packet : NoFallMode("Packet") {
    override fun onUpdate() {
        if (mc.thePlayer.fallDistance > 2f)
            sendPacket(C03PacketPlayer(true))
    }
}