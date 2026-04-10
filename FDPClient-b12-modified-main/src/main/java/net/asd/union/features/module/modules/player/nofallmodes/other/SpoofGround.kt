package net.asd.union.features.module.modules.player.nofallmodes.other

import net.asd.union.event.PacketEvent
import net.asd.union.features.module.modules.player.nofallmodes.NoFallMode
import net.minecraft.network.play.client.C03PacketPlayer

object SpoofGround : NoFallMode("SpoofGround") {
    override fun onPacket(event: PacketEvent) {
        if (event.packet is C03PacketPlayer)
            event.packet.onGround = true
    }
}