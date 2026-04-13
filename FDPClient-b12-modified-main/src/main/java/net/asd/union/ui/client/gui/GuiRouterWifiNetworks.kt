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
    private lateinit var connectButton: GuiButton
    private lateinit var disconnectButton: GuiButton
    private lateinit var backButton: GuiButton
    private lateinit var networksList: NetworksList
    private lateinit var passwordField: GuiTextField

    override fun initGui() {
        val centerX = width / 2 - 100
        val bottomY = height - 65

        networksList = NetworksList(this, top = 108, bottom = bottomY - 10)
        networksList.registerScrollButtons(7, 8)

        refreshButton = +GuiButton(1, centerX, bottomY, 64, 20, "Refresh")
        copyButton = +GuiButton(3, centerX + 68, bottomY, 64, 20, "Copy")
        connectButton = +GuiButton(5, centerX + 136, bottomY, 64, 20, "Connect")
        disconnectButton = +GuiButton(6, centerX, bottomY + 25, 96, 20, "Disconnect")
        private lateinit var hostField: GuiTextField
        private lateinit var portField: GuiTextField
        savePasswordButton = +GuiButton(4, centerX + 104, 78, 96, 20, "Save")
        backButton = +GuiButton(0, centerX + 104, bottomY + 25, 96, 20, "Back")

        passwordField = GuiTextField(10, Fonts.font35, centerX, 78, 96, 20).apply {
            maxStringLength = 64
            networksList = NetworksList(this, top = 128, bottom = bottomY - 10)
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

            hostField = GuiTextField(11, Fonts.font35, centerX, 100, 128, 18).apply {
                maxStringLength = 64
            }
            portField = GuiTextField(12, Fonts.font35, centerX + 132, 100, 64, 18).apply {
                maxStringLength = 5
                text = "45454"
            }
            3 -> copySelected()
            4 -> savePassword()
            5 -> connectSelected()
            6 -> disconnectPhone()
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


            Fonts.font35.drawStringWithShadow("Phone IP", (width / 2f) - 100f, 92f, Color.WHITE.rgb)
            hostField.drawTextBox()
            if (hostField.text.isEmpty() && !hostField.isFocused) {
                Fonts.font35.drawStringWithShadow("192.168.x.x", hostField.xPosition + 4f, 114f, 0xAAAAAA)
            }

            Fonts.font35.drawStringWithShadow("Port", (width / 2f) + 40f, 92f, Color.WHITE.rgb)
            portField.drawTextBox()
        val status = when {
            !ConnectToRouter.tunnelAvailable -> ""
            ConnectToRouter.wifiListInProgress -> "Device scan: loading…"
            ConnectToRouter.wifiListStatusLine.isNotBlank() -> ConnectToRouter.wifiListStatusLine
            else -> ""
        }
        if (status.isNotBlank()) {
            val color = when {
                ConnectToRouter.wifiListInProgress -> 0xFFFF55
                else -> ConnectToRouter.wifiListStatusColor
            }
            Fonts.font35.drawCenteredStringWithShadow(status, width / 2f, 47f, color)
        }

        val selected = ConnectToRouter.wifiNetworks.getOrNull(networksList.selectedSlot).orEmpty()
        if (selected.isNotBlank()) {
            Fonts.font35.drawCenteredStringWithShadow("Selected: ${formatDeviceLabel(selected)}", width / 2f, 60f, Color.WHITE.rgb)
        }

        networksList.drawScreen(mouseX, mouseY, partialTicks)

        refreshButton.enabled = !ConnectToRouter.wifiListInProgress
        copyButton.enabled = selected.isNotBlank()
        savePasswordButton.enabled = passwordField.text.trim().isNotEmpty()
        connectButton.enabled = selected.isNotBlank() && passwordField.text.trim().isNotEmpty()
            connectButton.enabled = passwordField.text.trim().isNotEmpty() &&
                (selected.isNotBlank() || hostField.text.trim().isNotEmpty())

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        networksList.handleMouseInput()
    }

            if (hostField.textboxKeyTyped(typedChar, keyCode)) {
                return
            }
            if (portField.textboxKeyTyped(typedChar, keyCode)) {
                return
            }
    override fun updateScreen() {
        passwordField.updateCursorCounter()
        hostField.updateCursorCounter()
        portField.updateCursorCounter()
        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (passwordField.textboxKeyTyped(typedChar, keyCode)) {
            return
        }
        when (keyCode) {
            Keyboard.KEY_ESCAPE -> {
                mc.displayGuiScreen(prevGui)
            hostField.mouseClicked(mouseX, mouseY, mouseButton)
            portField.mouseClicked(mouseX, mouseY, mouseButton)
                return
            }
            Keyboard.KEY_C -> {
            val selected = ConnectToRouter.wifiNetworks.getOrNull(networksList.selectedSlot)
            val manualHost = hostField.text.trim()
            val host = if (manualHost.isNotEmpty()) manualHost else selected?.let { parseDeviceHost(it) }.orEmpty()
            val port = if (manualHost.isNotEmpty()) {
                portField.text.trim().toIntOrNull() ?: 45454
            } else {
                selected?.let { parseDevicePort(it) } ?: 45454
            }
                }
            }
            if (host.isBlank()) {
                ClientUtils.displayAlert("Phone IP is empty")
                return
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
        val host = parseDeviceHost(ssid)
        GuiScreen.setClipboardString(host)
        ClientUtils.displayAlert("Copied device: ${formatDeviceLabel(ssid)}")
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

    private fun connectSelected() {
        val selected = ConnectToRouter.wifiNetworks.getOrNull(networksList.selectedSlot) ?: return
        val host = parseDeviceHost(selected)
        val port = parseDevicePort(selected)
        val password = passwordField.text.trim()

        Thread {
            val result = ConnectToRouter.requestPhoneConnect(host, port, password)
            ClientUtils.displayAlert(
                if (result.ok) "Phone tunnel connected" else "Connect failed: ${result.message.ifBlank { "Error" }}",
            )
            if (result.ok) {
                ConnectToRouter.refreshStatus()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun disconnectPhone() {
        Thread {
            val result = ConnectToRouter.requestPhoneDisconnect()
            ClientUtils.displayAlert(
                if (result.ok) "Phone tunnel disconnected" else "Disconnect failed: ${result.message.ifBlank { "Error" }}",
            )
            if (result.ok) {
                ConnectToRouter.refreshStatus()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun formatDeviceLabel(raw: String): String {
        val parts = raw.split("|")
        if (parts.size >= 2) {
            return "${parts[1]} (${parts[0]})"
        }
        return raw
    }

    private fun parseDeviceHost(raw: String): String {
        val parts = raw.split("|")
        return parts.getOrNull(0).orEmpty()
    }

    private fun parseDevicePort(raw: String): Int {
        val parts = raw.split("|")
        return parts.getOrNull(2)?.toIntOrNull() ?: 45454
    }

    private inner class NetworksList(prevGui: GuiScreen, top: Int, bottom: Int) :
        GuiSlot(mc, prevGui.width, prevGui.height, top, bottom, 24) {

        var selectedSlot = -1
            private set

        override fun getSize(): Int = ConnectToRouter.wifiNetworks.size

        override fun isSelected(id: Int): Boolean = selectedSlot == id

        override fun elementClicked(clickedElement: Int, doubleClick: Boolean, mouseX: Int, mouseY: Int) {
            selectedSlot = clickedElement
        }

        override fun drawSlot(id: Int, x: Int, y: Int, var4: Int, var5: Int, var6: Int) {
            val ssid = ConnectToRouter.wifiNetworks.getOrNull(id).orEmpty()
            Fonts.minecraftFont.drawStringWithShadow(ssid, (width / 2f) - 90f, y + 6f, Color.WHITE.rgb)
        }

        override fun drawBackground() {}
    }
}
