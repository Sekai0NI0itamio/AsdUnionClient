// FDPClient Hacked Client
// SpawnBotElement — custom ModuleElement for SpawnBot with an interactive bot management panel.
//
// Right-click opens a panel showing:
//   - "Spawn Bot" button
//   - Script selector (refreshed from disk)
//   - List of active bots with right-click → "View in popup window" option
package net.asd.union.ui.client.clickgui.elements

import net.asd.union.FDPClient.clickGui
import net.asd.union.features.module.modules.other.SpawnBot
import net.asd.union.ui.client.gui.GuiBotViewer
import net.asd.union.ui.font.Fonts.font35
import net.asd.union.utils.render.RenderUtils.drawRect
import net.asd.union.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.Minecraft
import java.awt.Color

class SpawnBotElement : ModuleElement(SpawnBot) {

    // Whether the SpawnBot management panel is open (replaces the normal settings panel)
    var showBotPanel = false

    // Context menu state: which bot row was right-clicked
    private var contextBotId: Int = -1
    private var contextMenuX: Int = 0
    private var contextMenuY: Int = 0

    // Cached panel geometry (set during draw, used for click detection)
    var panelX = 0
    var panelY = 0
    var panelW = 0
    var panelH = 0

    // Row geometry for bot list (botId → y range)
    private val botRowBounds = mutableMapOf<Int, IntRange>()

    companion object {
        const val PANEL_W = 160
        const val ROW_H = 14
        const val PADDING = 4
        const val SPAWN_BTN_H = 16
        const val SCRIPT_ROW_H = 12
        const val CONTEXT_MENU_W = 130
        const val CONTEXT_MENU_ITEM_H = 14

        val COL_PANEL_BG    = Color(30, 30, 35, 240).rgb
        val COL_PANEL_BORDER = Color(60, 60, 70).rgb
        val COL_SPAWN_BTN   = Color(50, 160, 80).rgb
        val COL_SPAWN_BTN_H = Color(60, 190, 100).rgb
        val COL_BOT_ROW     = Color(45, 45, 55).rgb
        val COL_BOT_ROW_H   = Color(60, 60, 75).rgb
        val COL_STATUS_ON   = Color(80, 220, 80).rgb
        val COL_STATUS_CONN = Color(255, 200, 50).rgb
        val COL_STATUS_OFF  = Color(160, 160, 160).rgb
        val COL_STATUS_ERR  = Color(220, 60, 60).rgb
        val COL_KILL_BTN    = Color(180, 50, 50).rgb
        val COL_KILL_BTN_H  = Color(220, 70, 70).rgb
        val COL_TEXT        = Color.WHITE.rgb
        val COL_TEXT_DIM    = Color(180, 180, 180).rgb
        val COL_CONTEXT_BG  = Color(25, 25, 30, 250).rgb
        val COL_CONTEXT_H   = Color(60, 100, 180).rgb
        val COL_SCRIPT_SEL  = Color(60, 100, 180).rgb
        val COL_SCRIPT_IDLE = Color(40, 40, 50).rgb
        val KILL_BTN_W      = 14
    }

    override fun drawScreenAndClick(mouseX: Int, mouseY: Int, mouseButton: Int?): Boolean {
        // Draw the standard module button first
        val handled = clickGui.style.drawModuleElementAndClick(mouseX, mouseY, this, mouseButton)

        if (!showBotPanel) return handled

        // Draw the SpawnBot management panel to the right of the module element
        val px = x + width + 4
        val py = y

        // Compute panel height dynamically
        val bots = SpawnBot.activeBots.toList()
        val scripts = SpawnBot.scriptsDir.listFiles { f -> f.extension == "js" }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

        val scriptSectionH = if (scripts.isNotEmpty()) PADDING + scripts.size * SCRIPT_ROW_H + PADDING else 0
        val botSectionH = if (bots.isNotEmpty()) PADDING + bots.size * (ROW_H + 2) + PADDING else 0
        val ph = PADDING + SPAWN_BTN_H + PADDING + scriptSectionH + botSectionH + PADDING

        panelX = px; panelY = py; panelW = PANEL_W; panelH = ph

        // Panel background
        drawRoundedRect(px.toFloat(), py.toFloat(), (px + PANEL_W).toFloat(), (py + ph).toFloat(), COL_PANEL_BG, 3f)

        var curY = py + PADDING

        // ── Spawn Bot button ──────────────────────────────────────────────────
        val spawnHovered = mouseX in px..px + PANEL_W && mouseY in curY..curY + SPAWN_BTN_H
        drawRoundedRect(
            (px + PADDING).toFloat(), curY.toFloat(),
            (px + PANEL_W - PADDING).toFloat(), (curY + SPAWN_BTN_H).toFloat(),
            if (spawnHovered) COL_SPAWN_BTN_H else COL_SPAWN_BTN, 3f
        )
        val spawnLabel = "▶  Spawn Bot"
        font35.drawString(
            spawnLabel,
            (px + PADDING + (PANEL_W - PADDING * 2 - font35.getStringWidth(spawnLabel)) / 2).toFloat(),
            (curY + 4).toFloat(),
            COL_TEXT
        )
        if (mouseButton == 0 && spawnHovered) {
            SpawnBot.spawnBot()
            clickGui.style.clickSound()
            return true
        }
        curY += SPAWN_BTN_H + PADDING

        // ── Script selector ───────────────────────────────────────────────────
        if (scripts.isNotEmpty()) {
            font35.drawString("Script:", (px + PADDING).toFloat(), (curY).toFloat(), COL_TEXT_DIM)
            curY += SCRIPT_ROW_H

            for (script in scripts) {
                val selected = SpawnBot.scriptsDir.let { dir ->
                    // Check if this matches the currently selected script value
                    script == SpawnBot.activeBots.firstOrNull()?.scriptName ||
                    script == (SpawnBot.values.find { it.name == "Script" }?.get() as? String)
                }
                val scriptHovered = mouseX in px..px + PANEL_W && mouseY in curY..curY + SCRIPT_ROW_H
                drawRect(
                    (px + PADDING).toFloat(), curY.toFloat(),
                    (px + PANEL_W - PADDING).toFloat(), (curY + SCRIPT_ROW_H).toFloat(),
                    if (selected) COL_SCRIPT_SEL else if (scriptHovered) COL_BOT_ROW_H else COL_SCRIPT_IDLE
                )
                font35.drawString(
                    if (selected) "✓ $script" else "  $script",
                    (px + PADDING + 2).toFloat(), (curY + 1).toFloat(),
                    if (selected) COL_TEXT else COL_TEXT_DIM
                )
                if (mouseButton == 0 && scriptHovered) {
                    // Select this script via the module's ListValue
                    SpawnBot.values.find { it.name == "Script" }?.let { v ->
                        @Suppress("UNCHECKED_CAST")
                        (v as? net.asd.union.config.ListValue)?.set(script)
                    }
                    clickGui.style.clickSound()
                    return true
                }
                curY += SCRIPT_ROW_H
            }
            curY += PADDING
        }

        // ── Active bot list ───────────────────────────────────────────────────
        botRowBounds.clear()
        if (bots.isNotEmpty()) {
            font35.drawString(
                "Active Bots (${bots.size}):",
                (px + PADDING).toFloat(), (curY).toFloat(), COL_TEXT_DIM
            )
            curY += SCRIPT_ROW_H

            for (bot in bots) {
                val rowTop = curY
                val rowBot = curY + ROW_H
                botRowBounds[bot.id] = rowTop..rowBot

                val rowHovered = mouseX in px..px + PANEL_W && mouseY in rowTop..rowBot
                drawRoundedRect(
                    (px + PADDING).toFloat(), rowTop.toFloat(),
                    (px + PANEL_W - PADDING).toFloat(), rowBot.toFloat(),
                    if (rowHovered) COL_BOT_ROW_H else COL_BOT_ROW, 2f
                )

                // Status dot
                val dotColor = when (bot.status) {
                    SpawnBot.BotStatus.ONLINE       -> COL_STATUS_ON
                    SpawnBot.BotStatus.CONNECTING   -> COL_STATUS_CONN
                    SpawnBot.BotStatus.DISCONNECTED -> COL_STATUS_OFF
                    SpawnBot.BotStatus.ERROR        -> COL_STATUS_ERR
                }
                drawRoundedRect(
                    (px + PADDING + 2).toFloat(), (rowTop + 4).toFloat(),
                    (px + PADDING + 7).toFloat(), (rowTop + 9).toFloat(),
                    dotColor, 2f
                )

                // Bot name
                val nameMaxW = PANEL_W - PADDING * 2 - KILL_BTN_W - 14
                val nameStr = truncate(bot.name, nameMaxW)
                font35.drawString(nameStr, (px + PADDING + 10).toFloat(), (rowTop + 2).toFloat(), COL_TEXT)

                // Ping
                if (bot.pingMs >= 0) {
                    val pingStr = "${bot.pingMs}ms"
                    font35.drawString(
                        pingStr,
                        (px + PANEL_W - PADDING - KILL_BTN_W - font35.getStringWidth(pingStr) - 4).toFloat(),
                        (rowTop + 2).toFloat(),
                        when {
                            bot.pingMs < 80  -> COL_STATUS_ON
                            bot.pingMs < 200 -> COL_STATUS_CONN
                            else             -> COL_STATUS_ERR
                        }
                    )
                }

                // Kill [×] button
                val killX = px + PANEL_W - PADDING - KILL_BTN_W
                val killHovered = mouseX in killX..killX + KILL_BTN_W && mouseY in rowTop..rowBot
                drawRoundedRect(
                    killX.toFloat(), (rowTop + 1).toFloat(),
                    (killX + KILL_BTN_W).toFloat(), (rowBot - 1).toFloat(),
                    if (killHovered) COL_KILL_BTN_H else COL_KILL_BTN, 2f
                )
                font35.drawString("×", (killX + 4).toFloat(), (rowTop + 2).toFloat(), COL_TEXT)

                if (mouseButton == 0 && killHovered) {
                    SpawnBot.killBot(bot.id)
                    clickGui.style.clickSound()
                    return true
                }

                // Right-click on bot row → show context menu
                if (mouseButton == 1 && rowHovered) {
                    contextBotId = bot.id
                    contextMenuX = mouseX
                    contextMenuY = mouseY
                    return true
                }

                curY += ROW_H + 2
            }
        }

        // ── Context menu ──────────────────────────────────────────────────────
        if (contextBotId != -1) {
            val cmX = contextMenuX
            val cmY = contextMenuY
            val cmH = CONTEXT_MENU_ITEM_H + PADDING * 2

            drawRoundedRect(
                cmX.toFloat(), cmY.toFloat(),
                (cmX + CONTEXT_MENU_W).toFloat(), (cmY + cmH).toFloat(),
                COL_CONTEXT_BG, 3f
            )

            val itemY = cmY + PADDING
            val itemHovered = mouseX in cmX..cmX + CONTEXT_MENU_W && mouseY in itemY..itemY + CONTEXT_MENU_ITEM_H
            drawRect(
                cmX.toFloat(), itemY.toFloat(),
                (cmX + CONTEXT_MENU_W).toFloat(), (itemY + CONTEXT_MENU_ITEM_H).toFloat(),
                if (itemHovered) COL_CONTEXT_H else 0
            )
            font35.drawString(
                "View in popup window",
                (cmX + PADDING).toFloat(), (itemY + 2).toFloat(), COL_TEXT
            )

            if (mouseButton == 0 && itemHovered) {
                val bot = SpawnBot.activeBots.firstOrNull { it.id == contextBotId }
                if (bot != null) {
                    Minecraft.getMinecraft().displayGuiScreen(GuiBotViewer(bot, clickGui))
                }
                contextBotId = -1
                return true
            }

            // Dismiss context menu on any click outside it
            if (mouseButton != null) {
                val inMenu = mouseX in cmX..cmX + CONTEXT_MENU_W && mouseY in cmY..cmY + cmH
                if (!inMenu) contextBotId = -1
            }
        }

        // Dismiss context menu if mouse moves far away (no button pressed)
        if (mouseButton == null && contextBotId != -1) {
            val cmX = contextMenuX; val cmY = contextMenuY
            val cmH = CONTEXT_MENU_ITEM_H + PADDING * 2
            if (mouseX !in cmX - 20..cmX + CONTEXT_MENU_W + 20 ||
                mouseY !in cmY - 20..cmY + cmH + 20) {
                contextBotId = -1
            }
        }

        return handled
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!isHovered(mouseX, mouseY)) {
            // Dismiss context menu if clicking outside the panel area
            if (contextBotId != -1) {
                val cmX = contextMenuX; val cmY = contextMenuY
                val cmH = CONTEXT_MENU_ITEM_H + PADDING * 2
                if (mouseX !in cmX..cmX + CONTEXT_MENU_W || mouseY !in cmY..cmY + cmH) {
                    contextBotId = -1
                }
            }
            return false
        }

        return when (mouseButton) {
            0 -> {
                // Left-click on the module row toggles the module
                SpawnBot.toggle()
                clickGui.style.clickSound()
                true
            }
            1 -> {
                // Right-click opens/closes the bot management panel
                showBotPanel = !showBotPanel
                if (showBotPanel) SpawnBot.refreshScriptList()
                contextBotId = -1
                clickGui.style.showSettingsSound()
                true
            }
            else -> false
        }
    }

    private fun truncate(text: String, maxWidth: Int): String {
        if (font35.getStringWidth(text) <= maxWidth) return text
        var result = text
        while (result.isNotEmpty() && font35.getStringWidth("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
    }
}
