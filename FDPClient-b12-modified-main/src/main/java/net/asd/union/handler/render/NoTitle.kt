/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.render

import net.minecraft.client.Minecraft

object NoTitle {
    var enabled = false
        set(value) {
            field = value

            if (value) {
                clearRenderedTitle()
            }
        }

    fun clearRenderedTitle() {
        Minecraft.getMinecraft().ingameGUI.displayTitle("", "", 0, 0, 0)
    }
}
