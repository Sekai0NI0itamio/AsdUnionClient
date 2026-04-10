/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.FDPClient.clickGui
import net.asd.union.config.*
import net.asd.union.event.PacketEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.client.clickgui.ClickGui
import net.asd.union.ui.client.clickgui.style.styles.BlackStyle
import net.asd.union.utils.client.ClientThemesUtils
import net.minecraft.network.play.server.S2EPacketCloseWindow
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClickGUIModule : Module("ClickGUI", Category.CLIENT, Keyboard.KEY_RSHIFT, canBeEnabled = false) {
    var lastScale = 0
    private val style by object :
        ListValue("Style", arrayOf("Black"), "Black") {
            override fun onChanged(oldValue: String, newValue: String) = updateStyle()
        }
    var scale by float("Scale", 0.8f, 0.5f..1.5f)
    val maxElements by int("MaxElements", 15, 1..30)
    val fadeSpeed by float("FadeSpeed", 1f, 0.5f..4f)
    val scrolls by boolean("Scrolls", true)
    val spacedModules by boolean("SpacedModules", false)
    val panelsForcedInBoundaries by boolean("PanelsForcedInBoundaries", false)

    // Keep these properties for compatibility with the FDP dropdown style, but they won't be used
    val categoryOutline by boolean("Header Outline", true) { true } // Always enabled for compatibility
    val backback by boolean("Background Accent", true) { true } // Always enabled for compatibility
    val scrollMode by choices("Scroll Mode", arrayOf("Screen Height", "Value"), "Value") { true } // Always enabled for compatibility
    val colormode by choices("Setting Accent", arrayOf("White", "Color"), "Color") { true } // Always enabled for compatibility
    val clickHeight by int("Tab Height", 250, 100..500) { true } // Always enabled for compatibility

    override fun onEnable() {
        lastScale = mc.gameSettings.guiScale
        mc.gameSettings.guiScale = 2

        updateStyle()
        mc.displayGuiScreen(clickGui)
        this.state = false
    }

    private fun updateStyle() {
        clickGui.style = BlackStyle
    }

    @JvmStatic
    fun generateColor(index: Int): Color {
        return ClientThemesUtils.getColor(index)
    }

    val onPacket = handler<PacketEvent>(always = true) { event ->
        if (event.packet is S2EPacketCloseWindow && mc.currentScreen is ClickGui) {
            event.cancelEvent()
        }
    }
}