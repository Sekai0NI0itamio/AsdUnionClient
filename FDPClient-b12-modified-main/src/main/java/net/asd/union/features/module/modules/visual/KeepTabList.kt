/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.client.settings.GameSettings

object KeepTabList : Module("KeepTabList", Category.VISUAL, gameDetecting = false, hideModule = false) {

    val onUpdate = handler<UpdateEvent> {
        if (mc.thePlayer == null || mc.theWorld == null) return@handler
        mc.gameSettings.keyBindPlayerList.pressed = true
    }
    override fun onDisable() {
        mc.gameSettings.keyBindPlayerList.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindPlayerList)
    }
}