package net.asd.union.ui.client.gui

import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard
import java.awt.Color

class GuiConnectToRouter(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var toggleButton: GuiButton
    private lateinit var refreshButton: GuiButton
    private lateinit var wifiNetworksButton: GuiButton
    private var statusTopY = 0f

    override fun initGui() {
        val buttonSpacing = 30
        val startY = height / 4 + 35
        val centerX = width / 2 - 100

        toggleButton = +GuiButton(
            1,
            centerX,
            startY,
            "ConnectToRouter (${if (ConnectToRouter.enabled) "On" else "Off"})",
        )
        refreshButton = +GuiButton(2, centerX, startY + buttonSpacing, "Refresh Status")
        wifiNetworksButton = +GuiButton(5, centerX, startY + buttonSpacing * 2, "Router Devices")
        +GuiButton(0, centerX, startY + buttonSpacing * 3, "Back")

        statusTopY = startY + buttonSpacing * 4f + 10f
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> {
                ConnectToRouter.enabled = !ConnectToRouter.enabled
                toggleButton.displayString = "ConnectToRouter (${if (ConnectToRouter.enabled) "On" else "Off"})"
                saveConfig(valuesConfig)
            }
            2 -> {
                ConnectToRouter.sendRefreshPacket()
                ConnectToRouter.refreshStatus()
            }
            5 -> {
                mc.displayGuiScreen(GuiRouterWifiNetworks(this))
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)

        Fonts.fontBold180.drawCenteredString("Connect to Router", width / 2f, height / 8f + 5f, 4673984, true)

        wifiNetworksButton.enabled = !ConnectToRouter.wifiListInProgress

        val statusLine = ConnectToRouter.statusLine
        val statusColor = ConnectToRouter.statusColor
        Fonts.font40.drawCenteredStringWithShadow(statusLine, width / 2f, statusTopY, statusColor)

        val tunnelText = if (ConnectToRouter.tunnelAvailable) {
            "Tunnel: running on port 25560"
        } else {
            "Tunnel: not running  (start router_tunnel first)"
        }
        val tunnelColor = if (ConnectToRouter.tunnelAvailable) 5635925 else 16746496
        Fonts.font35.drawCenteredStringWithShadow(tunnelText, width / 2f, statusTopY + 20f, tunnelColor)

        val ifaceText = when {
            ConnectToRouter.tunnelAvailable ->
                "Interface: ${ConnectToRouter.tunnelInterface.ifEmpty { "?" }}   IP: ${ConnectToRouter.tunnelIp.ifEmpty { "?" }}"

            ConnectToRouter.selectedInterface.isNotEmpty() ->
                "Interface: ${ConnectToRouter.selectedInterface}   IP: ${ConnectToRouter.lastLocalIp.ifEmpty { "?" }}"

            else -> "Interface: unknown"
        }
        Fonts.font35.drawCenteredStringWithShadow(ifaceText, width / 2f, statusTopY + 37f, Color.WHITE.rgb)

        val vpnText = "VPN detected: ${if (ConnectToRouter.vpnDetected) "Yes" else "No"}"
        Fonts.font35.drawCenteredStringWithShadow(vpnText, width / 2f, statusTopY + 54f, Color.WHITE.rgb)

        if (ConnectToRouter.wasAutoDisabled) {
            Fonts.font35.drawCenteredStringWithShadow(
                "Auto-disabled: ${ConnectToRouter.autoDisableReason}",
                width / 2f,
                statusTopY + 92f,
                16746496,
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}
