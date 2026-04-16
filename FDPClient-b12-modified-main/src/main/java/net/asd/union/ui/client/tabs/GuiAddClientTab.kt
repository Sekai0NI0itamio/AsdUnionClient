package net.asd.union.ui.client.tabs

import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.ui.client.gui.AltNameMode
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard

class GuiAddClientTab(private val returnScreen: GuiScreen?) : AbstractScreen() {

    companion object {
        private const val PANEL_WIDTH = 268
        private const val PANEL_HEIGHT = 184
        private const val MIN_LENGTH = 6
        private const val MAX_LENGTH = 16
    }

    private lateinit var usernameField: GuiTextField
    private lateinit var prefixField: GuiTextField
    private lateinit var modeButton: GuiButton
    private lateinit var rawButton: GuiButton

    private var draftMode = ClientTabManager.defaultMode()
    private var draftPrefix = ClientTabManager.defaultPrefix()
    private var draftLength = ClientTabManager.defaultLength().coerceIn(MIN_LENGTH, MAX_LENGTH)
    private var draftUnformatted = ClientTabManager.defaultUnformatted()

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        returnScreen?.setWorldAndResolution(mc, width, height)

        val left = panelLeft()
        val top = panelTop()
        val contentWidth = PANEL_WIDTH - 28

        usernameField = GuiTextField(0, mc.fontRendererObj, left + 14, top + 42, contentWidth, 20).apply {
            maxStringLength = 16
            text = generateSuggestedName()
            isFocused = true
        }

        prefixField = GuiTextField(1, mc.fontRendererObj, left + 14, top + 88, 114, 20).apply {
            maxStringLength = 16
            text = draftPrefix
        }

        modeButton = +GuiButton(2, left + 140, top + 88, 114, 20, "")
        rawButton = +GuiButton(3, left + 14, top + 114, 114, 20, "")

        +GuiButton(4, left + 140, top + 114, 24, 20, "-")
        +GuiButton(5, left + 230, top + 114, 24, 20, "+")

        +GuiButton(6, left + 14, top + 148, 74, 20, "Regenerate")
        +GuiButton(7, left + 97, top + 148, 74, 20, "Add Tab")
        +GuiButton(8, left + 180, top + 148, 74, 20, "Cancel")

        refreshButtons()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        super.onGuiClosed()
    }

    override fun updateScreen() {
        usernameField.updateCursorCounter()
        prefixField.updateCursorCounter()
        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        usernameField.textboxKeyTyped(typedChar, keyCode)
        prefixField.textboxKeyTyped(typedChar, keyCode)
        draftPrefix = prefixField.text.trim()

        if (keyCode == Keyboard.KEY_TAB) {
            val focusUsername = !usernameField.isFocused
            usernameField.isFocused = focusUsername
            prefixField.isFocused = !focusUsername
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            createTab()
            return
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(returnScreen)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        usernameField.mouseClicked(mouseX, mouseY, mouseButton)
        prefixField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            2 -> {
                draftMode = draftMode.next()
                if (draftMode != AltNameMode.STYLIZED) {
                    draftUnformatted = false
                }
                usernameField.text = generateSuggestedName()
            }

            3 -> {
                if (draftMode == AltNameMode.STYLIZED) {
                    draftUnformatted = !draftUnformatted
                    usernameField.text = generateSuggestedName()
                }
            }

            4 -> {
                draftLength = (draftLength - 1).coerceAtLeast(MIN_LENGTH)
                usernameField.text = generateSuggestedName()
            }

            5 -> {
                draftLength = (draftLength + 1).coerceAtMost(MAX_LENGTH)
                usernameField.text = generateSuggestedName()
            }

            6 -> usernameField.text = generateSuggestedName()
            7 -> createTab()
            8 -> mc.displayGuiScreen(returnScreen)
        }

        refreshButtons()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, width, height, 0xFF000000.toInt())

        val left = panelLeft()
        val top = panelTop()
        val right = left + PANEL_WIDTH
        val bottom = top + PANEL_HEIGHT

        drawRect(left, top, right, bottom, 0xF116191F.toInt())
        drawRect(left, top, right, top + 1, 0xFF5CC6FF.toInt())
        drawRect(left + 1, top + 1, right - 1, bottom - 1, 0xF11D2128.toInt())

        drawCenteredString(fontRendererObj, "New Session Tab", width / 2, top + 12, 0xFFFFFF)
        drawCenteredString(fontRendererObj, "Starts from the active tab config.", width / 2, top + 24, 0xA7B0BD)

        drawString(fontRendererObj, "Username", left + 14, top + 31, 0xCBD2DD)
        drawString(fontRendererObj, "Prefix", left + 14, top + 77, 0xCBD2DD)
        drawString(fontRendererObj, "Mode", left + 140, top + 77, 0xCBD2DD)
        drawString(fontRendererObj, "Extras", left + 14, top + 103, 0xCBD2DD)
        drawString(fontRendererObj, "Length", left + 140, top + 103, 0xCBD2DD)

        drawRect(left + 166, top + 114, left + 228, top + 134, 0x4A000000)
        drawCenteredString(fontRendererObj, draftLength.toString(), left + 197, top + 120, 0xFFFFFF)

        usernameField.drawTextBox()
        prefixField.drawTextBox()

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun doesGuiPauseGame() = false

    private fun refreshButtons() {
        modeButton.displayString = draftMode.displayName
        rawButton.displayString = if (draftMode == AltNameMode.STYLIZED) {
            "Raw: ${if (draftUnformatted) "On" else "Off"}"
        } else {
            "Raw: Auto"
        }
        rawButton.enabled = draftMode == AltNameMode.STYLIZED
    }

    private fun generateSuggestedName(): String {
        if (::prefixField.isInitialized) {
            draftPrefix = prefixField.text.trim()
        }
        return ClientTabManager.generateSuggestedUsername(draftMode, draftPrefix, draftLength, draftUnformatted)
    }

    private fun createTab() {
        val username = usernameField.text.trim()
        if (username.isEmpty()) {
            return
        }

        ClientTabManager.createOfflineTab(username, returnScreen)
    }

    private fun panelLeft() = width / 2 - PANEL_WIDTH / 2

    private fun panelTop() = height / 2 - PANEL_HEIGHT / 2
}
