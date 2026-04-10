/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.client.hud.designer.GuiHudDesigner

object HudDesigner : Module("HudDesigner", Category.CLIENT, canBeEnabled = false) {
    override fun onEnable() {
        mc.displayGuiScreen(GuiHudDesigner())
    }
}