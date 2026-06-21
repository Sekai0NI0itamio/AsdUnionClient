/*
 * FDPClient Hacked Client
 * BotStatusHUD — shows active SpawnBot bots in the HUD.
 *
 * Each row shows:  [×] BotName  <status>  <ping>ms
 * Hovering a row shows the server IP as a tooltip.
 * Clicking × terminates that bot.
 */
package net.asd.union.ui.client.hud.element.elements

import net.asd.union.config.boolean
import net.asd.union.config.font
import net.asd.union.features.module.modules.other.SpawnBot
import net.asd.union.ui.client.hud.element.Border
import net.asd.union.ui.client.hud.element.Element
import net.asd.union.ui.client.hud.element.ElementInfo
import net.asd.union.ui.client.hud.element.Side
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.render.RenderUtils.drawRect
import net.asd.union.utils.render.RenderUtils.drawRoundedRect
import java.awt.Color

@ElementInfo(name = "BotStatus")
class BotStatusHUD(
    x: Double = 2.0, y: Double = 60.0, scale: Float = 1F,
    side: Side = Side(Side.Horizontal.LEFT, Side.Vertical.UP)
) : Element(x, y, scale, side) {

    private val font by font("Font", Fonts.font40)
    private val shadow by boolean("Shadow", true)
    private val showWhenEmpty by boolean("ShowWhenEmpty", false)

    // Track which row the mouse is hovering over for tooltip
    private var hoveredBotId: Int = -1
    private var mouseXLocal: Double = 0.0
    private var mouseYLocal: Double = 0.0

    companion object {
        private const val ROW_HEIGHT = 12f
        private const val ROW_PADDING = 2f
        private const val KILL_BTN_WIDTH = 10f
        private const val PANEL_PADDING = 4f

        private val COLOR_ONLINE      = Color(80, 220, 80).rgb
        private val COLOR_CONNECTING  = Color(255, 200, 50).rgb
        private val COLOR_DISCONNECTED = Color(180, 180, 180).rgb
        private val COLOR_ERROR       = Color(220, 60, 60).rgb
        private val COLOR_KILL_BTN    = Color(200, 60, 60, 200).rgb
        private val COLOR_KILL_TEXT   = Color(255, 255, 255).rgb
        private val COLOR_BG          = Color(0, 0, 0, 140).rgb
        private val COLOR_HEADER      = Color(255, 255, 255, 200).rgb
        private val COLOR_TOOLTIP_BG  = Color(20, 20, 20, 220).rgb
        private val COLOR_TOOLTIP_TEXT = Color(200, 200, 200).rgb
    }

    override fun drawElement(): Border? {
        val bots = SpawnBot.activeBots.toList()

        if (bots.isEmpty() && !showWhenEmpty) return null

        assumeNonVolatile {
            val rowH = ROW_HEIGHT + ROW_PADDING
            val totalHeight = PANEL_PADDING * 2 + rowH * (bots.size + 1) // +1 for header
            val headerText = "§7Bots §f(${bots.size})"
            val headerWidth = font.getStringWidth(headerText)

            // Calculate panel width based on widest row
            var panelWidth = headerWidth + PANEL_PADDING * 2 + 10f
            for (bot in bots) {
                val rowText = buildRowText(bot)
                val w = KILL_BTN_WIDTH + 4f + font.getStringWidth(rowText) + PANEL_PADDING * 2
                if (w > panelWidth) panelWidth = w
            }

            // Background panel
            drawRoundedRect(0f, 0f, panelWidth, totalHeight, COLOR_BG, 3f)

            // Header
            font.drawString(headerText, PANEL_PADDING, PANEL_PADDING + 1f, COLOR_HEADER, shadow)

            // Bot rows
            bots.forEachIndexed { index, bot ->
                val rowY = PANEL_PADDING + rowH * (index + 1)

                // Row highlight on hover
                if (hoveredBotId == bot.id) {
                    drawRoundedRect(
                        PANEL_PADDING - 1f, rowY - 1f,
                        panelWidth - PANEL_PADDING + 1f, rowY + ROW_HEIGHT + 1f,
                        Color(255, 255, 255, 20).rgb, 2f
                    )
                }

                // Kill button [×]
                drawRoundedRect(
                    PANEL_PADDING, rowY,
                    PANEL_PADDING + KILL_BTN_WIDTH, rowY + ROW_HEIGHT,
                    COLOR_KILL_BTN, 2f
                )
                font.drawString(
                    "×",
                    PANEL_PADDING + 1.5f,
                    rowY + 1.5f,
                    COLOR_KILL_TEXT,
                    false
                )

                // Bot name + status + ping
                val rowText = buildRowText(bot)
                val statusColor = when (bot.status) {
                    SpawnBot.BotStatus.ONLINE       -> COLOR_ONLINE
                    SpawnBot.BotStatus.CONNECTING   -> COLOR_CONNECTING
                    SpawnBot.BotStatus.DISCONNECTED -> COLOR_DISCONNECTED
                    SpawnBot.BotStatus.ERROR        -> COLOR_ERROR
                }

                // Name in white, status indicator dot
                val nameText = "§f${bot.name}"
                val nameWidth = font.getStringWidth(nameText)
                font.drawString(nameText, PANEL_PADDING + KILL_BTN_WIDTH + 3f, rowY + 1.5f, -1, shadow)

                // Status dot
                val dotX = PANEL_PADDING + KILL_BTN_WIDTH + 3f + nameWidth + 3f
                drawRoundedRect(dotX, rowY + 3f, dotX + 5f, rowY + 8f, statusColor, 2f)

                // Ping
                if (bot.pingMs >= 0) {
                    val pingText = "${bot.pingMs}ms"
                    val pingColor = when {
                        bot.pingMs < 80  -> COLOR_ONLINE
                        bot.pingMs < 200 -> COLOR_CONNECTING
                        else             -> COLOR_ERROR
                    }
                    font.drawString(
                        pingText,
                        dotX + 8f,
                        rowY + 1.5f,
                        pingColor,
                        shadow
                    )
                }

                // Tooltip: show server IP when hovering this row
                if (hoveredBotId == bot.id) {
                    val tipText = "§7${bot.serverIp}"
                    val tipW = font.getStringWidth(tipText) + 6f
                    val tipX = mouseXLocal.toFloat()
                    val tipY = mouseYLocal.toFloat() - 14f
                    drawRoundedRect(tipX, tipY, tipX + tipW, tipY + 11f, COLOR_TOOLTIP_BG, 2f)
                    font.drawString(tipText, tipX + 3f, tipY + 1.5f, COLOR_TOOLTIP_TEXT, false)
                }
            }
        }

        val totalHeight = PANEL_PADDING * 2 + (ROW_HEIGHT + ROW_PADDING) * (bots.size + 1)
        val panelWidth = 120f // approximate; exact width computed above but we need a static value here
        return Border(0f, 0f, panelWidth, totalHeight)
    }

    override fun handleMouseClick(x: Double, y: Double, mouseButton: Int) {
        if (mouseButton != 0) return

        val bots = SpawnBot.activeBots.toList()
        val rowH = (ROW_HEIGHT + ROW_PADDING).toDouble()

        bots.forEachIndexed { index, bot ->
            val rowY = PANEL_PADDING + rowH * (index + 1)
            // Check if click is on the kill button
            if (x >= PANEL_PADDING && x <= PANEL_PADDING + KILL_BTN_WIDTH &&
                y >= rowY && y <= rowY + ROW_HEIGHT
            ) {
                SpawnBot.killBot(bot.id)
            }
        }
    }

    override fun updateElement() {
        // Track mouse position for hover/tooltip (updated via render loop)
        val scaledMouse = net.minecraft.client.gui.ScaledResolution(mc)
        val rawX = org.lwjgl.input.Mouse.getX()
        val rawY = org.lwjgl.input.Mouse.getY()
        val scaleF = scaledMouse.scaleFactor.toDouble()
        mouseXLocal = (rawX / scaleF) - renderX
        mouseYLocal = ((mc.displayHeight - rawY) / scaleF) - renderY

        // Determine hovered bot
        val bots = SpawnBot.activeBots.toList()
        val rowH = (ROW_HEIGHT + ROW_PADDING).toDouble()
        hoveredBotId = -1
        bots.forEachIndexed { index, bot ->
            val rowY = PANEL_PADDING + rowH * (index + 1)
            if (mouseXLocal >= PANEL_PADDING && mouseYLocal >= rowY && mouseYLocal <= rowY + ROW_HEIGHT) {
                hoveredBotId = bot.id
            }
        }
    }

    private fun buildRowText(bot: SpawnBot.BotEntry): String {
        val statusStr = when (bot.status) {
            SpawnBot.BotStatus.ONLINE       -> "online"
            SpawnBot.BotStatus.CONNECTING   -> "connecting"
            SpawnBot.BotStatus.DISCONNECTED -> "offline"
            SpawnBot.BotStatus.ERROR        -> "error"
        }
        val pingStr = if (bot.pingMs >= 0) " ${bot.pingMs}ms" else ""
        return "${bot.name} $statusStr$pingStr"
    }
}
