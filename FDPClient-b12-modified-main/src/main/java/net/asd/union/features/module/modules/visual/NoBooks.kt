/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.PacketEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.minecraft.network.play.server.S3FPacketCustomPayload

object NoBooks : Module("NoBooks", Category.VISUAL, gameDetecting = false, hideModule = false) {

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is S3FPacketCustomPayload && packet.channelName == "MC|BOpen") event.cancelEvent()
    }
}