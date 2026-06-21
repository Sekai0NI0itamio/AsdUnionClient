// FDPClient Hacked Client
// GuiBotViewer — popup window showing a bot's script, console output, chat, and current command.
package net.asd.union.ui.client.gui

import net.asd.union.features.module.modules.other.SpawnBot
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.render.RenderUtils.drawRect
import net.asd.union.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color

class GuiBotViewer(
    private val bot: SpawnBot.BotEntry,
    private val returnScreen: GuiScreen?
) : GuiScreen() {

    private val font = Fonts.font35

    // Window geometry (computed in drawScreen)
    private var winX = 0f
    private var winY = 0f
    private var winW = 0f
    private var winH = 0f

    // Scroll offset for the log area (in lines)
    private var scrollOffset = 0
    private var autoScroll = true

    // Tab: 0 = All output, 1 = Script source only, 2 = Console/errors only
    private var activeTab = 0

    private val TABS = arrayOf("All Output", "Script Source", "Console / Errors")

    // Colors
    private val COL_BG         = Color(245, 245, 245).rgb
    private val COL_HEADER_BG  = Color(50, 50, 60).rgb
    private val COL_HEADER_TXT = Color(255, 255, 255).rgb
    private val COL_TAB_ACTIVE = Color(80, 130, 220).rgb
    private val COL_TAB_IDLE   = Color(180, 180, 190).rgb
    private val COL_TAB_TXT    = Color(255, 255, 255).rgb
    private val COL_STATUS_BG  = Color(230, 230, 235).rgb
    private val COL_LOG_BG     = Color(255, 255, 255).rgb
    private val COL_LOG_TXT    = Color(30, 30, 30).rgb
    private val COL_LOG_ERR    = Color(200, 40, 40).rgb
    private val COL_LOG_CMD    = Color(40, 120, 200).rgb
    private val COL_LOG_CHAT   = Color(40, 160, 80).rgb
    private val COL_LOG_SCRIPT = Color(100, 60, 160).rgb
    private val COL_CLOSE_BTN  = Color(220, 60, 60).rgb
    private val COL_KILL_BTN   = Color(200, 80, 40).rgb
    private val COL_SCROLLBAR  = Color(180, 180, 200).rgb
    private val COL_SCROLLBAR_THUMB = Color(100, 100, 130).rgb
    private val COL_BORDER     = Color(200, 200, 210).rgb

    private val LINE_H = 11f
    private val PADDING = 8f
    private val HEADER_H = 28f
    private val TAB_H = 20f
    private val STATUS_H = 22f
    private val SCROLLBAR_W = 6f
    private val CLOSE_BTN_W = 20f
    private val KILL_BTN_W = 50f

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Dim background
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), Color(0, 0, 0, 160).rgb)

        // Window size: 80% of screen
        winW = width * 0.82f
        winH = height * 0.84f
        winX = (width - winW) / 2f
        winY = (height - winH) / 2f

        GlStateManager.pushMatrix()

        // Main window background
        drawRoundedRect(winX, winY, winX + winW, winY + winH, COL_BG, 4f)

        // Header bar
        drawRoundedRect(winX, winY, winX + winW, winY + HEADER_H, COL_HEADER_BG, 4f)
        // Flatten bottom corners of header
        drawRect(winX, winY + HEADER_H - 4f, winX + winW, winY + HEADER_H, COL_HEADER_BG)

        // Title
        val statusStr = when (bot.status) {
            SpawnBot.BotStatus.ONLINE       -> "§aonline"
            SpawnBot.BotStatus.CONNECTING   -> "§eyconnecting"
            SpawnBot.BotStatus.DISCONNECTED -> "§7offline"
            SpawnBot.BotStatus.ERROR        -> "§cerror"
        }
        val pingStr = if (bot.pingMs >= 0) "  ${bot.pingMs}ms" else ""
        font.drawString(
            "Bot: §f${bot.name}  $statusStr§r$pingStr  §7${bot.serverIp}  §8[${bot.scriptName}.js]",
            winX + PADDING, winY + 9f, COL_HEADER_TXT
        )

        // Close button [×]
        val closeBtnX = winX + winW - CLOSE_BTN_W - PADDING
        val closeBtnY = winY + 5f
        drawRoundedRect(closeBtnX, closeBtnY, closeBtnX + CLOSE_BTN_W, closeBtnY + 18f, COL_CLOSE_BTN, 3f)
        font.drawString("×", closeBtnX + 7f, closeBtnY + 4f, COL_HEADER_TXT)

        // Kill bot button
        val killBtnX = closeBtnX - KILL_BTN_W - 4f
        val killBtnY = closeBtnY
        val botAlive = bot.status == SpawnBot.BotStatus.ONLINE || bot.status == SpawnBot.BotStatus.CONNECTING
        if (botAlive) {
            drawRoundedRect(killBtnX, killBtnY, killBtnX + KILL_BTN_W, killBtnY + 18f, COL_KILL_BTN, 3f)
            font.drawString("Kill Bot", killBtnX + 6f, killBtnY + 4f, COL_HEADER_TXT)
        }

        // Tab bar
        val tabY = winY + HEADER_H
        var tabX = winX
        val tabW = winW / TABS.size
        TABS.forEachIndexed { i, label ->
            val col = if (i == activeTab) COL_TAB_ACTIVE else COL_TAB_IDLE
            drawRect(tabX, tabY, tabX + tabW, tabY + TAB_H, col)
            font.drawString(label, tabX + (tabW - font.getStringWidth(label)) / 2f, tabY + 5f, COL_TAB_TXT)
            tabX += tabW
        }

        // Status bar (current command)
        val statusBarY = winY + HEADER_H + TAB_H
        drawRect(winX, statusBarY, winX + winW, statusBarY + STATUS_H, COL_STATUS_BG)
        font.drawString(
            "§7Current: §f${bot.currentCommand}",
            winX + PADDING, statusBarY + 5f, COL_LOG_TXT
        )

        // Log area
        val logAreaY = statusBarY + STATUS_H
        val logAreaH = winH - HEADER_H - TAB_H - STATUS_H
        val logAreaW = winW - SCROLLBAR_W - 2f

        drawRect(winX, logAreaY, winX + logAreaW, logAreaY + logAreaH, COL_LOG_BG)

        // Gather lines for current tab
        val lines = getFilteredLines()

        // Auto-scroll to bottom
        val visibleLines = ((logAreaH - PADDING) / LINE_H).toInt()
        if (autoScroll) scrollOffset = maxOf(0, lines.size - visibleLines)
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, lines.size - visibleLines))

        // Clip and draw lines
        GlStateManager.pushMatrix()
        // Scissor-like clipping via translate + careful y bounds check
        val startLine = scrollOffset
        val endLine = minOf(lines.size, startLine + visibleLines + 1)
        for (i in startLine until endLine) {
            val lineY = logAreaY + PADDING / 2f + (i - startLine) * LINE_H
            if (lineY + LINE_H < logAreaY || lineY > logAreaY + logAreaH) continue
            val (text, color) = lines[i]
            font.drawString(text, winX + PADDING, lineY, color)
        }
        GlStateManager.popMatrix()

        // Scrollbar
        val sbX = winX + logAreaW + 1f
        drawRect(sbX, logAreaY, sbX + SCROLLBAR_W, logAreaY + logAreaH, COL_SCROLLBAR)
        if (lines.size > visibleLines) {
            val thumbH = (visibleLines.toFloat() / lines.size * logAreaH).coerceAtLeast(16f)
            val thumbY = logAreaY + (scrollOffset.toFloat() / (lines.size - visibleLines)) * (logAreaH - thumbH)
            drawRoundedRect(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, COL_SCROLLBAR_THUMB, 2f)
        }

        // Border
        drawRoundedRect(winX, winY, winX + winW, winY + winH, COL_BORDER, 4f)

        GlStateManager.popMatrix()

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun getFilteredLines(): List<Pair<String, Int>> {
        val raw = bot.log.toList()
        return when (activeTab) {
            1 -> raw.filter { it.startsWith("=== Script:") || it.startsWith("=== End") ||
                    (!it.startsWith("---") && !it.startsWith("{") && !it.startsWith("[chat]") &&
                     raw.indexOf(it).let { idx ->
                         val scriptStart = raw.indexOfFirst { l -> l.startsWith("=== Script:") }
                         val scriptEnd   = raw.indexOfFirst { l -> l.startsWith("=== End") }
                         idx in scriptStart..scriptEnd
                     }) }
                .map { colorLine(it) }
            2 -> raw.filter { !it.startsWith("=== Script:") && !it.startsWith("=== End") &&
                    !it.startsWith("--- Bot output") }
                .map { colorLine(it) }
            else -> raw.map { colorLine(it) }
        }
    }

    private fun colorLine(line: String): Pair<String, Int> {
        return when {
            line.startsWith("=== Script:") || line.startsWith("=== End") -> line to COL_LOG_SCRIPT
            line.startsWith("--- Bot output") -> line to Color(150, 150, 150).rgb
            line.startsWith("[chat]")          -> line to COL_LOG_CHAT
            line.contains("error", ignoreCase = true) ||
            line.contains("Error", ignoreCase = false) ||
            line.contains("exception", ignoreCase = true) -> line to COL_LOG_ERR
            line.startsWith("{\"type\":\"cmd\"") -> line to COL_LOG_CMD
            line.startsWith("{")               -> line to Color(120, 120, 120).rgb
            else                               -> line to COL_LOG_TXT
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton != 0) return

        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()

        // Close button
        val closeBtnX = winX + winW - CLOSE_BTN_W - PADDING
        val closeBtnY = winY + 5f
        if (mx in closeBtnX..closeBtnX + CLOSE_BTN_W && my in closeBtnY..closeBtnY + 18f) {
            mc.displayGuiScreen(returnScreen)
            return
        }

        // Kill bot button
        val killBtnX = closeBtnX - KILL_BTN_W - 4f
        val killBtnY = closeBtnY
        val botAlive = bot.status == SpawnBot.BotStatus.ONLINE || bot.status == SpawnBot.BotStatus.CONNECTING
        if (botAlive && mx in killBtnX..killBtnX + KILL_BTN_W && my in killBtnY..killBtnY + 18f) {
            SpawnBot.killBot(bot.id)
            return
        }

        // Tab clicks
        val tabY = winY + HEADER_H
        val tabW = winW / TABS.size
        if (my in tabY..tabY + TAB_H) {
            val tabIdx = ((mx - winX) / tabW).toInt().coerceIn(0, TABS.size - 1)
            activeTab = tabIdx
            autoScroll = true
            return
        }

        // Click outside window → close
        if (mx < winX || mx > winX + winW || my < winY || my > winY + winH) {
            mc.displayGuiScreen(returnScreen)
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getDWheel()
        if (wheel != 0) {
            autoScroll = false
            scrollOffset -= wheel / 30
            scrollOffset = scrollOffset.coerceAtLeast(0)
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(returnScreen)
        }
    }

    override fun doesGuiPauseGame() = false
}
