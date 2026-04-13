package net.asd.union.ui.client.gui

import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard
import java.awt.Color

class GuiRouterWifiNetworks(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var refreshButton: GuiButton
    private lateinit var copyButton: GuiButton
    private lateinit var savePasswordButton: GuiButton
    private lateinit var backButton: GuiButton
    private lateinit var networksList: NetworksList
    private lateinit var passwordField: GuiTextField

    override fun initGui() {
        val centerX = width / 2 - 100
        val bottomY = height - 65

        networksList = NetworksList(this, top = 108, bottom = bottomY - 10)
        networksList.registerScrollButtons(7, 8)

        refreshButton = +GuiButton(1, centerX, bottomY, 96, 20, "Refresh")
        copyButton = +GuiButton(3, centerX + 104, bottomY, 96, 20, "Copy IP")
        savePasswordButton = +GuiButton(4, centerX + 104, 78, 96, 20, "Save")
        backButton = +GuiButton(0, centerX, bottomY + 25, 200, 20, "Back")

        passwordField = GuiTextField(10, Fonts.font35, centerX, 78, 96, 20).apply {
            maxStringLength = 64
            text = ConnectToRouter.phonePassword
        }

        val now = System.currentTimeMillis()
        val stale = now - ConnectToRouter.wifiNetworksUpdatedAtMs > 30_000
        if (!ConnectToRouter.wifiListInProgress && (ConnectToRouter.wifiNetworks.isEmpty() || stale)) {
            ConnectToRouter.refreshWifiNetworksThroughTunnel()
        }
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> ConnectToRouter.refreshWifiNetworksThroughTunnel()
            3 -> copySelected()
            4 -> savePassword()
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)

        Fonts.fontBold180.drawCenteredString("Tunnel Devices", width / 2f, 10f, 4673984, true)

        val subtitle = "Nearby devices (${ConnectToRouter.wifiNetworks.size})"
        Fonts.font35.drawCenteredStringWithShadow(subtitle, width / 2f, 32f, Color.WHITE.rgb)

        Fonts.font35.drawStringWithShadow("Phone password", (width / 2f) - 100f, 64f, Color.WHITE.rgb)
        passwordField.drawTextBox()
        if (passwordField.text.isEmpty() && !passwordField.isFocused) {
            Fonts.font35.drawStringWithShadow("Set password", passwordField.xPosition + 4f, 83f, 0xAAAAAA)
        }

        val status = when {
            !ConnectToRouter.tunnelAvailable -> ""
            ConnectToRouter.wifiCommandInProgress -> ConnectToRouter.wifiCommandStatusLine.ifBlank { "Wi-Fi: connecting…" }
            ConnectToRouter.wifiCommandStatusLine.isNotBlank() -> ConnectToRouter.wifiCommandStatusLine
            ConnectToRouter.wifiListInProgress -> "Device scan: loading…"
            ConnectToRouter.wifiListStatusLine.isNotBlank() -> ConnectToRouter.wifiListStatusLine
            else -> ""
        }
        if (status.isNotBlank()) {
            val color = when {
                ConnectToRouter.wifiCommandInProgress -> 0xFFFF55
                ConnectToRouter.wifiCommandStatusLine.isNotBlank() -> ConnectToRouter.wifiCommandStatusColor
                ConnectToRouter.wifiListInProgress -> 0xFFFF55
                else -> ConnectToRouter.wifiListStatusColor
            }
            Fonts.font35.drawCenteredStringWithShadow(status, width / 2f, 47f, color)
        }

        val selected = ConnectToRouter.wifiNetworks.getOrNull(networksList.selectedSlot).orEmpty()
        if (selected.isNotBlank()) {
            Fonts.font35.drawCenteredStringWithShadow("Selected: $selected", width / 2f, 60f, Color.WHITE.rgb)
        }

        networksList.drawScreen(mouseX, mouseY, partialTicks)

        refreshButton.enabled = !ConnectToRouter.wifiListInProgress
        copyButton.enabled = selected.isNotBlank()
        savePasswordButton.enabled = passwordField.text.trim().isNotEmpty()

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        networksList.handleMouseInput()
    }

    override fun updateScreen() {
        passwordField.updateCursorCounter()
        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (passwordField.textboxKeyTyped(typedChar, keyCode)) {
            return
        }
        when (keyCode) {
            Keyboard.KEY_ESCAPE -> {
                mc.displayGuiScreen(prevGui)
                return
            }
            Keyboard.KEY_C -> {
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
                    copySelected()
                    return
                }
            }
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        passwordField.mouseClicked(mouseX, mouseY, mouseButton)
    }

    private fun copySelected() {
        val index = networksList.selectedSlot
        val ssid = ConnectToRouter.wifiNetworks.getOrNull(index) ?: return
        GuiScreen.setClipboardString(ssid)
        ClientUtils.displayAlert("Copied device: $ssid")
    }

    private fun savePassword() {
        val value = passwordField.text.trim()
        if (value.isBlank()) {
            ClientUtils.displayAlert("Password is empty")
            return
        }

        ConnectToRouter.setPhonePassword(value)
        val ok = ConnectToRouter.writePhonePasswordFile()
        ClientUtils.displayAlert(if (ok) "Phone password saved" else "Failed to save password")
    }

    private inner class NetworksList(prevGui: GuiScreen, top: Int, bottom: Int) :
        GuiSlot(mc, prevGui.width, prevGui.height, top, bottom, 24) {

        var selectedSlot = -1
            private set

        override fun getSize(): Int = ConnectToRouter.wifiNetworks.size

        override fun isSelected(id: Int): Boolean = selectedSlot == id

        override fun elementClicked(clickedElement: Int, doubleClick: Boolean, mouseX: Int, mouseY: Int) {
            selectedSlot = clickedElement
            val ssid = ConnectToRouter.wifiNetworks.getOrNull(clickedElement) ?: return
            ConnectToRouter.setRequestedWifiSsid(ssid)
            if (doubleClick) {
                ConnectToRouter.connectWifiThroughTunnel(ssid)
            }
        }

        override fun drawSlot(id: Int, x: Int, y: Int, var4: Int, var5: Int, var6: Int) {
            val ssid = ConnectToRouter.wifiNetworks.getOrNull(id).orEmpty()
            Fonts.minecraftFont.drawStringWithShadow(ssid, (width / 2f) - 90f, y + 6f, Color.WHITE.rgb)
        }

        override fun drawBackground() {}
    }
}
