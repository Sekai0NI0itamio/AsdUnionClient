/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player

import net.asd.union.event.MotionEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.inventory.InventoryUtils
import net.asd.union.config.choices
import net.asd.union.event.handler
import net.minecraft.init.Items
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement

object KeepAlive : Module("KeepAlive", Category.PLAYER) {

    val mode by choices("Mode", arrayOf("/heal", "Soup"), "/heal")

    private var runOnce = false


    val onMotion = handler<MotionEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.isDead || thePlayer.health <= 0) {
            if (runOnce) return@handler

            when (mode.lowercase()) {
                "/heal" -> thePlayer.sendChatMessage("/heal")
                "soup" -> {
                    val soupInHotbar = InventoryUtils.findItem(36, 44, Items.mushroom_stew)

                    if (soupInHotbar != null) {
                        SilentHotbar.selectSlotSilently(this,
                            soupInHotbar,
                            immediate = true,
                            render = false,
                            resetManually = true
                        )
                        sendPacket(C08PacketPlayerBlockPlacement(thePlayer.heldItem))
                        SilentHotbar.resetSlot(this)
                    }
                }
            }

            runOnce = true
        } else
            runOnce = false
    }
}