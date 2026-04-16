package net.asd.union.ui.client.tabs

import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen

class GuiTabDecisionScreen(
    private val returnScreen: GuiScreen?,
    private val titleText: String,
    private val messageText: String,
    private val confirmText: String,
    private val onConfirm: () -> Unit
) : AbstractScreen() {

    override fun initGui() {
        returnScreen?.setWorldAndResolution(mc, width, height)
        val panelLeft = width / 2 - 120
        val buttonY = height / 2 + 38

        +GuiButton(0, panelLeft + 10, buttonY, 106, 20, confirmText)
        +GuiButton(1, panelLeft + 124, buttonY, 106, 20, "Cancel")
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> onConfirm()
            1 -> mc.displayGuiScreen(returnScreen)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, width, height, 0xFF000000.toInt())

        val panelLeft = width / 2 - 120
        val panelTop = height / 2 - 60
        val panelRight = width / 2 + 120
        val panelBottom = height / 2 + 70

        drawRect(panelLeft, panelTop, panelRight, panelBottom, -0x6f000000)
        drawRect(panelLeft, panelTop, panelRight, panelTop + 1, -0x3600bc)

        drawCenteredString(fontRendererObj, titleText, width / 2, panelTop + 12, 0xFFFFFF)

        val lines = fontRendererObj.listFormattedStringToWidth(messageText, 220)
        lines.forEachIndexed { index, line ->
            drawCenteredString(fontRendererObj, line, width / 2, panelTop + 34 + index * (fontRendererObj.FONT_HEIGHT + 2), 0xD8D8D8)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun doesGuiPauseGame() = false
}
