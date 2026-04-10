/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.client.gui

import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.utils.io.MiscUtils
import net.asd.union.utils.render.RenderUtils.drawBloom
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import java.awt.Color

class GuiInfo(private val prevGui: GuiScreen) : AbstractScreen() {

    override fun initGui() {
        val yOffset = height / 4 + 20
        val buttonWidth = 200
        val buttonHeight = 20

        val buttons = listOf(
            +GuiButton(1, width / 2 - buttonWidth / 2, yOffset + buttonHeight * 0, "Scripts"),
            +GuiButton(2, width / 2 - buttonWidth / 2, yOffset + buttonHeight * 1 + 10, "Client Configuration"),
            +GuiButton(3, width / 2 - buttonWidth / 2, yOffset + buttonHeight * 2 + 20, "Done")
        )

        buttonList.addAll(buttons)

        super.initGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {

        assumeNonVolatile = true

        drawDefaultBackground()

        drawBloom(mouseX - 5, mouseY - 5, 10, 10, 16, Color(guiColor))

        assumeNonVolatile = false

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(prevGui)
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            1 -> mc.displayGuiScreen(GuiScripts(this))
            2 -> mc.displayGuiScreen(GuiClientConfiguration(this))
            3 -> mc.displayGuiScreen(prevGui)
        }
    }
}
