package net.asd.union.ui.client.gui

import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import java.awt.Color

class GuiConnectToRouter(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var toggleButton: GuiButton
    private lateinit var refreshButton: GuiButton

    override fun initGui() {
        val buttonSpacing = 30

        toggleButton = +GuiButton(
            1,
            width / 2 - 100,
            height / 4 + 35,
            "ConnectToRouter (${if (ConnectToRouter.enabled) "On" else "Off"})",
        )
        refreshButton = +GuiButton(2, width / 2 - 100, height / 4 + 35 + buttonSpacing, "Refresh Status")
        +GuiButton(0, width / 2 - 100, height / 4 + 35 + buttonSpacing * 3, "Back")
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
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)

        Fonts.fontBold180.drawCenteredString("Connect to Router", width / 2f, height / 8f + 5f, 4673984, true)

        val statusLine = ConnectToRouter.statusLine
        val statusColor = ConnectToRouter.statusColor
        Fonts.font40.drawCenteredStringWithShadow(statusLine, width / 2f, height / 4f + 105f, statusColor)

        val tunnelText = if (ConnectToRouter.tunnelAvailable) {
            "Tunnel: running on port 25560"
        } else {
            "Tunnel: not running  (start router_tunnel first)"
        }
        val tunnelColor = if (ConnectToRouter.tunnelAvailable) 5635925 else 16746496
        Fonts.font35.drawCenteredStringWithShadow(tunnelText, width / 2f, height / 4f + 125f, tunnelColor)

        val ifaceText = when {
            ConnectToRouter.tunnelAvailable ->
                "Interface: ${ConnectToRouter.tunnelInterface.ifEmpty { "?" }}   IP: ${ConnectToRouter.tunnelIp.ifEmpty { "?" }}"

            ConnectToRouter.selectedInterface.isNotEmpty() ->
                "Interface: ${ConnectToRouter.selectedInterface}   IP: ${ConnectToRouter.lastLocalIp.ifEmpty { "?" }}"

            else -> "Interface: unknown"
        }
        Fonts.font35.drawCenteredStringWithShadow(ifaceText, width / 2f, height / 4f + 142f, Color.WHITE.rgb)

        val vpnText = "VPN detected: ${if (ConnectToRouter.vpnDetected) "Yes" else "No"}"
        Fonts.font35.drawCenteredStringWithShadow(vpnText, width / 2f, height / 4f + 159f, Color.WHITE.rgb)

        if (ConnectToRouter.wasAutoDisabled) {
            Fonts.font35.drawCenteredStringWithShadow(
                "Auto-disabled: ${ConnectToRouter.autoDisableReason}",
                width / 2f,
                height / 4f + 180f,
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
