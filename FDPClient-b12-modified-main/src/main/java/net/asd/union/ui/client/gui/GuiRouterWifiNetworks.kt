package net.asd.union.ui.client.gui

import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import org.lwjgl.input.Keyboard
import java.awt.Color

class GuiRouterWifiNetworks(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var refreshButton: GuiButton
    private lateinit var copyButton: GuiButton
    private lateinit var connectButton: GuiButton
    private lateinit var backButton: GuiButton
    private lateinit var networksList: NetworksList

    override fun initGui() {
        val centerX = width / 2 - 100
        val bottomY = height - 65

        networksList = NetworksList(this, top = 72, bottom = bottomY - 10)
        networksList.registerScrollButtons(7, 8)

        refreshButton = +GuiButton(1, centerX, bottomY, 64, 20, "Refresh")
        copyButton = +GuiButton(3, centerX + 68, bottomY, 64, 20, "Copy")
        connectButton = +GuiButton(2, centerX + 136, bottomY, 64, 20, "Connect")
        backButton = +GuiButton(0, centerX, bottomY + 25, 200, 20, "Back")

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
            2 -> connectSelected()
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)

        Fonts.fontBold180.drawCenteredString("Wi-Fi Networks", width / 2f, 10f, 4673984, true)

        val subtitle = "Saved networks (${ConnectToRouter.wifiNetworks.size})"
        Fonts.font35.drawCenteredStringWithShadow(subtitle, width / 2f, 32f, Color.WHITE.rgb)

        val status = when {
            !ConnectToRouter.tunnelAvailable -> ""
            ConnectToRouter.wifiCommandInProgress -> ConnectToRouter.wifiCommandStatusLine.ifBlank { "Wi-Fi: connecting…" }
            ConnectToRouter.wifiCommandStatusLine.isNotBlank() -> ConnectToRouter.wifiCommandStatusLine
            ConnectToRouter.wifiListInProgress -> "Wi-Fi list: loading…"
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
        connectButton.enabled = !ConnectToRouter.wifiCommandInProgress &&
            networksList.selectedSlot >= 0 &&
            networksList.selectedSlot < ConnectToRouter.wifiNetworks.size

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        networksList.handleMouseInput()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        when (keyCode) {
            Keyboard.KEY_ESCAPE -> {
                mc.displayGuiScreen(prevGui)
                return
            }
            Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                connectSelected()
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

    private fun connectSelected() {
        val index = networksList.selectedSlot
        val ssid = ConnectToRouter.wifiNetworks.getOrNull(index) ?: return
        ConnectToRouter.setRequestedWifiSsid(ssid)
        ConnectToRouter.connectWifiThroughTunnel(ssid)
    }

    private fun copySelected() {
        val index = networksList.selectedSlot
        val ssid = ConnectToRouter.wifiNetworks.getOrNull(index) ?: return
        GuiScreen.setClipboardString(ssid)
        ClientUtils.displayAlert("Copied SSID: $ssid")
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
