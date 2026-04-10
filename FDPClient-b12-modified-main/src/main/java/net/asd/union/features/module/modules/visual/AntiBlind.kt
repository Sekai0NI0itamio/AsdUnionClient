/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.PacketEvent
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean
import net.asd.union.config.float
import net.asd.union.event.handler
import net.minecraft.network.play.server.S3FPacketCustomPayload

object AntiBlind : Module("AntiBlind", Category.VISUAL, gameDetecting = false, hideModule = false) {
    val confusionEffect by boolean("Confusion", true)
    val pumpkinEffect by boolean("Pumpkin", true)
    val fireEffect by float("FireAlpha", 0.3f, 0f..1f)
    val bossHealth by boolean("BossHealth", true)
    private val bookPage by boolean("BookPage", true)
    val achievements by boolean("Achievements", true)

    val onPacket = handler<PacketEvent> { event ->
        if (!bookPage) return@handler
        val packet = event.packet
        if (packet is S3FPacketCustomPayload && packet.channelName == "MC|BOpen") event.cancelEvent()
    }
}