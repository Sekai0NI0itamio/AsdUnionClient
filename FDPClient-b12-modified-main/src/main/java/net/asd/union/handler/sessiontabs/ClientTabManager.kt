package net.asd.union.handler.sessiontabs

import net.asd.union.event.EventManager
import net.asd.union.event.GameTickEvent
import net.asd.union.event.Listenable
import net.asd.union.event.Render2DEvent
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.file.FileManager
import net.asd.union.ui.client.gui.AltNameMode
import net.asd.union.ui.client.gui.GuiClientConfiguration
import net.asd.union.ui.client.gui.GuiMainMenu
import net.asd.union.ui.client.tabs.GuiAddClientTab
import net.asd.union.ui.client.tabs.GuiTabDecisionScreen
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.client.ServerUtils
import net.asd.union.handler.render.LazyChunkCache
import net.asd.union.utils.render.MiniMapRegister
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.util.Session
import org.lwjgl.input.Mouse
import java.awt.Color
import java.util.UUID

object ClientTabManager : Listenable, MinecraftInstance {

    private const val BAR_Y = 0
    private const val BAR_HEIGHT = 28
    private const val CONTENT_GAP = 8
    private const val TAB_PADDING = 10
    private const val TAB_GAP = 4
    private const val MIN_TAB_WIDTH = 90
    private const val MAX_TAB_WIDTH = 170
    private const val RIGHT_RESERVED = 96
    private const val PLUS_WIDTH = 18

    private val tabs = mutableListOf<ClientTab>()
    private var activeTabId: String? = null
    private var bootstrapped = false
    private var persistenceSuspendDepth = 0
    private var contextMenu: ContextMenu? = null
    private var leftMouseDown = false
    private var rightMouseDown = false
    private var tabBarVisible = false
    private var scheduledSwitch: Pair<String, GuiScreen?>? = null

    private val activeTab: ClientTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    fun currentTabId(): String? = activeTabId

    fun switchToTabById(tabId: String): Boolean {
        if (tabs.none { it.id == tabId }) return false
        requestSwitch(tabId, null)
        return true
    }

    fun configSnapshotForTab(tabId: String): TabConfigSnapshot? =
        tabs.firstOrNull { it.id == tabId }?.configSnapshot

    fun updateConfigSnapshot(tabId: String, snapshot: TabConfigSnapshot) {
        tabs.firstOrNull { it.id == tabId }?.configSnapshot = snapshot
    }

    fun sessionForTab(tabId: String): Session? =
        tabs.firstOrNull { it.id == tabId }?.sessionSnapshot?.toMinecraftSession()

    fun shouldRunFullBackgroundSimulation(tabId: String): Boolean {
        val runtime = LiveTabRuntimeManager.runtimeFor(tabId) ?: return false
        return runtime.hasWorldState
    }

    private fun clearPerTabRenderCaches(tabId: String) {
        LazyChunkCache.clearContext(tabId)
        MiniMapRegister.clearContext(tabId)
    }

    fun bootstrap() {
        if (bootstrapped) return

        val sessionSnapshot = SessionSnapshot.fromMinecraft() ?: SessionSnapshot.offline("Player")
        tabs += ClientTab(
            displayName = sessionSnapshot.username,
            sessionSnapshot = sessionSnapshot,
            configSnapshot = TabConfigSnapshot.captureCurrentState(),
            lastServerAddress = mc.currentServerData?.serverIP
        )
        activeTabId = tabs.firstOrNull()?.id
        bootstrapped = true
    }

    fun canPersistToMainStorage(): Boolean {
        if (!bootstrapped) {
            return true
        }

        return persistenceSuspendDepth == 0
    }

    fun contentTop(screen: GuiScreen?): Int {
        return if (tabBarVisible && shouldRenderOn(screen)) BAR_Y + BAR_HEIGHT + CONTENT_GAP else 0
    }

    fun isTabBarVisible() = tabBarVisible

    fun setTabBarVisible(visible: Boolean) {
        if (tabBarVisible == visible) {
            return
        }

        tabBarVisible = visible
        contextMenu = null
    }

    fun setTabStatus(tabId: String, status: TabStatus, detail: String? = null) {
        tabs.find { it.id == tabId }?.let {
            it.status = status
            it.statusDetail = detail
        }
    }

    fun getTabStatus(tabId: String): Pair<TabStatus, String?>? {
        return tabs.find { it.id == tabId }?.let { Pair(it.status, it.statusDetail) }
    }

    fun toggleTabBarVisible() {
        setTabBarVisible(!tabBarVisible)
    }

    fun renderOnScreen(screen: GuiScreen, mouseX: Int, mouseY: Int) {
        if (!shouldRenderOn(screen) || !tabBarVisible) {
            if (contextMenu != null) {
                contextMenu = null
            }
            return
        }

        drawOverlay(screen.width, screen.height, mouseX, mouseY)
    }

    fun handleScreenMouseClick(screen: GuiScreen, mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!shouldRenderOn(screen) || !tabBarVisible) {
            if (contextMenu != null) {
                contextMenu = null
            }
            return false
        }

        return handleMouseClick(screen, mouseX, mouseY, mouseButton)
    }

    fun createOfflineTab(username: String, returnScreen: GuiScreen?) {
        val tabId = createOfflineTabBackground(username)
        if (tabId != null) {
            switchToTab(tabId, returnScreen)
        }
    }

    /**
     * Create an offline tab without switching to it.
     * Returns the new tab's ID, or null if the username was empty.
     */
    fun createOfflineTabBackground(username: String): String? {
        bootstrap()

        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            return null
        }

        val newTab = ClientTab(
            displayName = cleanUsername,
            sessionSnapshot = SessionSnapshot.offline(cleanUsername),
            configSnapshot = TabConfigSnapshot.fromSavedMainStorage()
        )

        tabs += newTab
        contextMenu = null
        return newTab.id
    }

    fun generateSuggestedUsername(
        mode: AltNameMode,
        prefix: String,
        length: Int,
        unformatted: Boolean
    ): String {
        return net.asd.union.utils.kotlin.RandomUtils.randomUsername(prefix, length, unformatted)
    }

    fun defaultMode() = GuiClientConfiguration.altNameMode

    fun defaultPrefix() = GuiClientConfiguration.altsPrefix

    fun defaultLength() = GuiClientConfiguration.altsLength

    fun defaultUnformatted() = GuiClientConfiguration.unformattedAlts

    private fun shouldRenderOn(screen: GuiScreen?): Boolean {
        bootstrap()
        return true
    }

    private fun captureActiveTabState() {
        val tab = activeTab ?: return
        SessionSnapshot.fromMinecraft()?.let {
            tab.sessionSnapshot = it
            tab.displayName = it.username
        }
        tab.lastServerAddress = mc.currentServerData?.serverIP ?: tab.lastServerAddress
        tab.configSnapshot = TabConfigSnapshot.captureCurrentState()
    }

    private fun applyTab(tabId: String, captureCurrent: Boolean) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (captureCurrent && activeTabId != tabId) {
            captureActiveTabState()
        }

        activeTabId = tabId
        contextMenu = null

        persistenceSuspendDepth++
        try {
            mc.session = target.sessionSnapshot.toMinecraftSession()
            target.configSnapshot.applyToClient()
            val serverData = target.lastServerAddress?.let { ServerData("", it, false) }
            ServerUtils.serverData = serverData
            mc.setServerData(serverData)
            EventManager.call(SessionUpdateEvent)
        } finally {
            persistenceSuspendDepth--
        }
    }

    private fun requestSwitch(tabId: String, returnScreen: GuiScreen?) {
        if (activeTabId == tabId) {
            return
        }

        // Defer the switch to the next tick to prevent NPE when switching
        // from within a mouse click handler. The current screen's handleInput
        // loop may still reference mc.thePlayer after clearMinecraftRuntime
        // nulls it, causing NPE in GuiContainer.mouseClicked.
        scheduledSwitch = tabId to returnScreen
    }

    private fun switchToTab(tabId: String, returnScreen: GuiScreen?) {
        val previousActiveTabId = activeTabId
        val hadLiveWorld = mc.theWorld != null
        System.out.println("[TabDebug][switchToTab] === START switchToTab ===")
        System.out.println("[TabDebug][switchToTab] from=$previousActiveTabId to=$tabId, " +
            "hadLiveWorld=$hadLiveWorld, returnScreen=${returnScreen?.javaClass?.simpleName}")
        System.out.println("[TabDebug][switchToTab] mc BEFORE: theWorld=${mc.theWorld != null}, " +
            "thePlayer=${mc.thePlayer != null}, controller=${mc.playerController != null}, " +
            "currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
        System.out.flush()

        System.out.println("[TabDebug][switchToTab] STEP 1: captureCurrentRuntime($previousActiveTabId)")
        System.out.flush()
        val capturedPreviousState = previousActiveTabId != null && LiveTabRuntimeManager.captureCurrentRuntime(previousActiveTabId)
        System.out.println("[TabDebug][switchToTab] STEP 1 result: captureCurrentRuntime=$capturedPreviousState")
        System.out.flush()

        if (previousActiveTabId != null) {
            System.out.println("[TabDebug][switchToTab] STEP 2: deactivate previous tab $previousActiveTabId")
            System.out.flush()
            LiveTabRuntimeManager.prepareRuntimeForBackground(previousActiveTabId)
            LiveTabRuntimeManager.deactivateRuntime(previousActiveTabId)
            System.out.println("[TabDebug][switchToTab] STEP 2 done: deactivated $previousActiveTabId")
            System.out.flush()
        }

        System.out.println("[TabDebug][switchToTab] STEP 3: applyTab($tabId)")
        System.out.flush()
        applyTab(tabId, captureCurrent = true)
        System.out.println("[TabDebug][switchToTab] STEP 3 done: session/config applied. " +
            "mc.session=${mc.session?.username}")
        System.out.flush()

        // Save previous tab's chat and restore new tab's chat
        TabChatManager.saveCurrentAndSwitch(previousActiveTabId, tabId)

        System.out.println("[TabDebug][switchToTab] STEP 4: activateRuntime($tabId)")
        System.out.flush()
        val activated = LiveTabRuntimeManager.activateRuntime(tabId)
        System.out.println("[TabDebug][switchToTab] STEP 4 result: activateRuntime=$activated")
        System.out.flush()

        if (activated) {
            System.out.println("[TabDebug][switchToTab] STEP 5: Switch completed. " +
                "Post-state: mc.theWorld=${mc.theWorld != null}, mc.thePlayer=${mc.thePlayer != null}, " +
                "mc.currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
            System.out.println("[TabDebug][switchToTab] === END switchToTab (activated) ===")
            System.out.flush()
            return
        }

        if (capturedPreviousState && hadLiveWorld) {
            val fallbackScreen = if (returnScreen == null || returnScreen is GuiIngameMenu
                || returnScreen is net.minecraft.client.gui.inventory.GuiContainer) {
                GuiMainMenu()
            } else {
                returnScreen
            }
            System.out.println("[TabDebug][switchToTab] STEP 6: clearMinecraftRuntime($fallbackScreen)")
            System.out.flush()
            LiveTabRuntimeManager.clearMinecraftRuntime(fallbackScreen)
            System.out.println("[TabDebug][switchToTab] === END switchToTab (cleared) ===")
            System.out.flush()
            return
        }

        val safeReturnScreen = if (returnScreen is net.minecraft.client.gui.inventory.GuiContainer) {
            GuiMainMenu()
        } else {
            returnScreen
        }
        System.out.println("[TabDebug][switchToTab] STEP 7: displayGuiScreen($safeReturnScreen)")
        System.out.flush()
        mc.displayGuiScreen(safeReturnScreen)
        System.out.println("[TabDebug][switchToTab] === END switchToTab (displayGuiScreen) ===")
        System.out.flush()
    }

    private fun requestClose(tabId: String, returnScreen: GuiScreen?) {
        if (tabs.size <= 1) {
            ClientUtils.displayAlert("You need at least one tab open.")
            return
        }

        val target = tabs.firstOrNull { it.id == tabId } ?: return
        val description = if (target.id == activeTabId) {
            "Close ${target.displayName}? If this tab is active, its current world will be disconnected."
        } else {
            "Close ${target.displayName}?"
        }

        mc.displayGuiScreen(
            GuiTabDecisionScreen(
                returnScreen,
                "Close Tab?",
                description,
                "Close Tab"
            ) {
                closeTab(tabId, returnScreen)
            }
        )
    }

    private fun closeTab(tabId: String, returnScreen: GuiScreen?) {
        val tabToRemove = tabs.firstOrNull { it.id == tabId } ?: return
        val wasActive = tabToRemove.id == activeTabId
        val fallback = tabs.firstOrNull { it.id != tabId }

        tabs.remove(tabToRemove)
        contextMenu = null

        if (!wasActive) {
            TabChatManager.clearTab(tabId)
            LiveTabRuntimeManager.removeRuntime(tabId)
            clearPerTabRenderCaches(tabId)
            mc.displayGuiScreen(returnScreen)
            return
        }

        val previousActiveTabId = activeTabId

        if (mc.theWorld != null && activeTabId != null) {
            LiveTabRuntimeManager.captureCurrentRuntime(activeTabId!!)
            LiveTabRuntimeManager.deactivateRuntime(activeTabId)
        }

        TabChatManager.clearTab(tabId)
        LiveTabRuntimeManager.removeRuntime(tabId)
        clearPerTabRenderCaches(tabId)

        if (fallback != null) {
            applyTab(fallback.id, captureCurrent = false)
            TabChatManager.saveCurrentAndSwitch(previousActiveTabId, fallback.id)
            if (!LiveTabRuntimeManager.activateRuntime(fallback.id)) {
                LiveTabRuntimeManager.clearMinecraftRuntime(GuiMainMenu())
            }
        } else {
            activeTabId = null
            TabChatManager.saveCurrentAndSwitch(previousActiveTabId, null)
            LiveTabRuntimeManager.clearMinecraftRuntime(GuiMainMenu())
        }
    }

    private fun openAddTabScreen(returnScreen: GuiScreen?) {
        mc.displayGuiScreen(GuiAddClientTab(returnScreen))
    }

    private fun drawOverlay(screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int) {
        val layout = buildLayout(screenWidth)
        val accent = Color(guiColor)
        val barTop = BAR_Y
        val barBottom = BAR_Y + BAR_HEIGHT

        Gui.drawRect(0, barTop, screenWidth, barBottom, Color(14, 14, 18, 210).rgb)
        Gui.drawRect(0, barBottom, screenWidth, barBottom + 1, Color(accent.red, accent.green, accent.blue, 180).rgb)

        val font = mc.fontRendererObj
        val baseTextY = BAR_Y + (BAR_HEIGHT - font.FONT_HEIGHT) / 2

        layout.tabs.forEach { tabLayout ->
            val hovered = tabLayout.contains(mouseX, mouseY)
            val active = tabLayout.id == activeTabId
            val tab = tabs.find { it.id == tabLayout.id }
            val isWaiting = tab?.status == TabStatus.WAITING
            val fillColor = when {
                active -> Color(accent.red, accent.green, accent.blue, 185).rgb
                hovered -> Color(55, 55, 64, 220).rgb
                else -> Color(34, 34, 42, 200).rgb
            }
            val borderColor = Color(0, 0, 0, 80).rgb

            Gui.drawRect(tabLayout.x, BAR_Y + 2, tabLayout.x + tabLayout.width, BAR_Y + BAR_HEIGHT - 2, fillColor)
            Gui.drawRect(tabLayout.x, BAR_Y + 2, tabLayout.x + tabLayout.width, BAR_Y + 3, borderColor)

            // Yellow text for waiting tabs, white for normal
            val labelColor = if (isWaiting) 0xFFFFFF55.toInt() else 0xFFFFFF
            font.drawStringWithShadow(tabLayout.label, (tabLayout.x + TAB_PADDING).toFloat(), baseTextY.toFloat(), labelColor)

            val closeHover = tabLayout.isCloseHovered(mouseX, mouseY)
            val closeColor = if (closeHover) Color(255, 110, 110).rgb else Color(220, 220, 220).rgb
            font.drawStringWithShadow("x", (tabLayout.closeX).toFloat(), baseTextY.toFloat(), closeColor)
        }

        val plusColor = if (layout.plusRect.contains(mouseX, mouseY)) {
            Color(accent.red, accent.green, accent.blue, 210).rgb
        } else {
            Color(44, 44, 52, 220).rgb
        }
        Gui.drawRect(layout.plusRect.left, layout.plusRect.top, layout.plusRect.right, layout.plusRect.bottom, plusColor)
        font.drawStringWithShadow("+", (layout.plusRect.left + 6).toFloat(), baseTextY.toFloat(), 0xFFFFFF)

        // Draw tooltip for hovered tabs with status info
        val hoveredTab = layout.tabs.find { it.contains(mouseX, mouseY) && !it.isCloseHovered(mouseX, mouseY) }
        if (hoveredTab != null) {
            val tab = tabs.find { it.id == hoveredTab.id }
            if (tab != null) {
                val tooltipLines = mutableListOf<String>()
                tooltipLines.add("\u00a7e${tab.displayName}")
                if (tab.status == TabStatus.WAITING) {
                    tooltipLines.add("\u00a7eStatus: \u00a7fWaiting")
                    tab.statusDetail?.let { tooltipLines.add("\u00a77$it") }
                }
                tab.lastServerAddress?.let { tooltipLines.add("\u00a77Server: \u00a7f$it") }
                if (tooltipLines.size > 1) {
                    val sr = ScaledResolution(mc)
                    val drawX = mouseX.coerceAtMost(sr.scaledWidth - 150)
                    val drawY = (BAR_Y + BAR_HEIGHT + 4).coerceAtMost(sr.scaledHeight - tooltipLines.size * 12 - 10)
                    net.minecraft.client.gui.Gui.drawRect(drawX - 4, drawY - 4, drawX + 150, drawY + tooltipLines.size * 12 + 4, 0xE0101010.toInt())
                    tooltipLines.forEachIndexed { i, line ->
                        font.drawStringWithShadow(line, drawX.toFloat(), (drawY + i * 12).toFloat(), 0xFFFFFF)
                    }
                }
            }
        }
    }

    private fun handleMouseClick(returnScreen: GuiScreen?, mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        val layout = buildLayout(ScaledResolution(mc).scaledWidth)
        val inBar = mouseY in BAR_Y..(BAR_Y + BAR_HEIGHT)

        contextMenu?.let { menu ->
            val inMenu = mouseX in menu.x..(menu.x + MENU_WIDTH) && mouseY in menu.y..(menu.y + MENU_HEIGHT)
            if (inMenu) {
                contextMenu = null
                return true
            }

            contextMenu = null
            if (!inBar) {
                return false
            }
        }

        if (layout.plusRect.contains(mouseX, mouseY)) {
            if (mouseButton == 0) {
                openAddTabScreen(returnScreen)
            }
            return true
        }

        val clickedTab = layout.tabs.firstOrNull { it.contains(mouseX, mouseY) }
        if (clickedTab != null) {
            when (mouseButton) {
                0 -> {
                    if (clickedTab.isCloseHovered(mouseX, mouseY)) {
                        requestClose(clickedTab.id, returnScreen)
                    } else {
                        requestSwitch(clickedTab.id, returnScreen)
                    }
                }
            }
            return true
        }

        return inBar
    }

    private fun buildLayout(screenWidth: Int): OverlayLayout {
        val font = mc.fontRendererObj
        val layouts = mutableListOf<TabLayout>()
        var x = 8
        val maxRight = screenWidth - RIGHT_RESERVED

        tabs.forEach { tab ->
            val label = ellipsize(tab.displayName, 22)
            val width = (font.getStringWidth(label) + TAB_PADDING * 2 + 10).coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)

            if (x + width > maxRight) {
                return@forEach
            }

            layouts += TabLayout(
                id = tab.id,
                label = label,
                x = x,
                width = width
            )
            x += width + TAB_GAP
        }

        val plusLeft = screenWidth - RIGHT_RESERVED + ((RIGHT_RESERVED - PLUS_WIDTH) / 2)
        return OverlayLayout(layouts, OverlayRect(plusLeft, BAR_Y + 3, plusLeft + PLUS_WIDTH, BAR_Y + BAR_HEIGHT - 3))
    }

    private fun ellipsize(input: String, maxLength: Int): String {
        if (input.length <= maxLength) {
            return input
        }

        return input.substring(0, maxLength - 3) + "..."
    }

    private fun pollIngameMouse() {
        if (mc.currentScreen != null || !shouldRenderOn(null) || !tabBarVisible || Mouse.isGrabbed()) {
            if (Mouse.isGrabbed() && contextMenu != null) {
                contextMenu = null
            }
            leftMouseDown = Mouse.isButtonDown(0)
            rightMouseDown = Mouse.isButtonDown(1)
            return
        }

        val scaledResolution = ScaledResolution(mc)
        val mouseX = Mouse.getX() * scaledResolution.scaledWidth / mc.displayWidth
        val mouseY = scaledResolution.scaledHeight - Mouse.getY() * scaledResolution.scaledHeight / mc.displayHeight - 1

        val leftDown = Mouse.isButtonDown(0)
        val rightDown = Mouse.isButtonDown(1)

        if (leftDown && !leftMouseDown) {
            handleMouseClick(null, mouseX, mouseY, 0)
        }

        if (rightDown && !rightMouseDown) {
            handleMouseClick(null, mouseX, mouseY, 1)
        }

        leftMouseDown = leftDown
        rightMouseDown = rightDown
    }

    val onRender2D = handler<Render2DEvent>(always = true) {
        if (mc.currentScreen != null || !shouldRenderOn(null) || !tabBarVisible) {
            return@handler
        }

        val scaledResolution = ScaledResolution(mc)
        val mouseX = Mouse.getX() * scaledResolution.scaledWidth / mc.displayWidth
        val mouseY = scaledResolution.scaledHeight - Mouse.getY() * scaledResolution.scaledHeight / mc.displayHeight - 1
        drawOverlay(scaledResolution.scaledWidth, scaledResolution.scaledHeight, mouseX, mouseY)
    }

    val onTick = handler<GameTickEvent>(always = true) {
        if (SessionRuntimeScope.isDetachedContextActive()) {
            return@handler
        }

        // Process deferred tab switch
        val switch = scheduledSwitch
        if (switch != null) {
            scheduledSwitch = null
            switchToTab(switch.first, switch.second)
        }

        // Tick the automation system
        net.asd.union.handler.automation.ClientAutomation.tick()

        // Tick the multi-select join queue
        MultiSelectJoinQueue.tick()

        activeTab?.let { tab ->
            mc.currentServerData?.serverIP?.let { tab.lastServerAddress = it }
        }
        LiveTabRuntimeManager.syncActiveRuntime(activeTabId)
        LiveTabRuntimeManager.tickBackgroundRuntimes(activeTabId)
        TabChatManager.drainBackgroundChatQueue()
        pollIngameMouse()
    }

    val onSessionUpdate = handler<SessionUpdateEvent>(always = true) {
        if (!bootstrapped) {
            return@handler
        }

        SessionSnapshot.fromMinecraft()?.let { snapshot ->
            activeTab?.sessionSnapshot = snapshot
            activeTab?.displayName = snapshot.username
        }
    }

    private data class ClientTab(
        val id: String = UUID.randomUUID().toString(),
        var displayName: String,
        var sessionSnapshot: SessionSnapshot,
        var configSnapshot: TabConfigSnapshot,
        var lastServerAddress: String? = null,
        var status: TabStatus = TabStatus.NORMAL,
        var statusDetail: String? = null
    )

    enum class TabStatus {
        NORMAL,   // Default — nothing pending or finished
        WAITING   // Yellow — waiting in queue to connect
    }

    private data class SessionSnapshot(
        val username: String,
        val uuid: String,
        val token: String,
        val type: String
    ) {
        fun toMinecraftSession() = Session(username, uuid, token, type)

        companion object {
            fun fromMinecraft(): SessionSnapshot? {
                val session = MinecraftInstance.mc.session ?: return null
                val username = session.username ?: return null
                val uuid = session.playerID ?: return null
                val token = session.token ?: "-"
                val type = session.sessionType?.name?.lowercase() ?: "legacy"
                return SessionSnapshot(username, uuid, token, type)
            }

            fun offline(username: String): SessionSnapshot {
                val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()
                return SessionSnapshot(username, uuid, "-", "legacy")
            }
        }
    }

    private data class OverlayLayout(
        val tabs: List<TabLayout>,
        val plusRect: OverlayRect
    )

    private data class TabLayout(
        val id: String,
        val label: String,
        val x: Int,
        val width: Int
    ) {
        val closeX: Int
            get() = x + width - 12

        fun contains(mouseX: Int, mouseY: Int): Boolean {
            return mouseX in x..(x + width) && mouseY in (BAR_Y + 2)..(BAR_Y + BAR_HEIGHT - 2)
        }

        fun isCloseHovered(mouseX: Int, mouseY: Int): Boolean {
            return mouseX in (x + width - 16)..(x + width - 4) && mouseY in (BAR_Y + 2)..(BAR_Y + BAR_HEIGHT - 2)
        }
    }

    private data class OverlayRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun contains(mouseX: Int, mouseY: Int): Boolean {
            return mouseX in left..right && mouseY in top..bottom
        }
    }

    private data class ContextMenu(
        val tabId: String,
        val x: Int,
        val y: Int
    )

    private const val MENU_WIDTH = 110
    private const val MENU_ITEM_HEIGHT = 20
    private const val MENU_HEIGHT = MENU_ITEM_HEIGHT * 1
}
