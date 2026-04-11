/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player


import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.boolean
import net.asd.union.event.handler
import net.minecraft.client.gui.GuiGameOver

object AutoRespawn : Module("AutoRespawn", Category.PLAYER, gameDetecting = false, hideModule = false) {

    private val instant by boolean("Instant", true)

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer

        if (thePlayer == null)
            return@handler

        if (if (instant) mc.thePlayer.health == 0F || mc.thePlayer.isDead else mc.currentScreen is GuiGameOver && (mc.currentScreen as GuiGameOver).enableButtonsTimer >= 20) {
            thePlayer.respawnPlayer()
            mc.displayGuiScreen(null)
        }
    }
}
