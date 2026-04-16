package net.asd.union.handler.sessiontabs

import net.asd.union.config.BoolValue
import net.asd.union.config.FloatValue
import net.asd.union.config.IntegerValue
import net.asd.union.event.EventManager
import net.asd.union.event.GameTickEvent
import net.asd.union.event.Listenable
import net.asd.union.event.Render2DEvent
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.features.module.modules.combat.KillAura
import net.asd.union.features.module.modules.combat.KillAuraTargeter
import net.asd.union.features.module.modules.other.LinkBots
import net.asd.union.file.FileManager
import net.asd.union.ui.client.gui.AltNameMode
import net.asd.union.ui.client.gui.GuiClientConfiguration
import net.asd.union.ui.client.gui.GuiMainMenu
import net.asd.union.ui.client.tabs.GuiAddClientTab
import net.asd.union.ui.client.tabs.GuiTabDecisionScreen
import net.asd.union.utils.attack.CooldownHelper.getAttackCooldownProgress
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.client.ServerUtils
import net.asd.union.utils.extensions.attackEntityWithModifiedSprint
import net.asd.union.utils.extensions.center
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.asd.union.utils.extensions.hitBox
import net.asd.union.utils.extensions.setSprintSafely
import net.asd.union.utils.pathing.BaritoneGoalNear
import net.asd.union.utils.pathing.BaritoneNavigationSession
import net.asd.union.utils.pathing.NavigationSettings
import net.asd.union.utils.pathing.PathFollowCommand
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.asd.union.utils.timing.TimeUtils.randomClickDelay
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemSword
import net.minecraft.util.BlockPos
import net.minecraft.util.Session
import net.minecraft.util.Vec3
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
    private const val MENU_WIDTH = 110
    private const val MENU_ITEM_HEIGHT = 20
    private const val MENU_HEIGHT = MENU_ITEM_HEIGHT * 2
    private const val LINK_BOT_FOLLOW_RANGE = 1
    private const val LINK_BOT_SUCCESS_RADIUS = 1.35
    private const val LINK_BOT_FOCUS_PADDING = 1.0

    private val tabs = mutableListOf<ClientTab>()
    private var activeTabId: String? = null
    private var bootstrapped = false
    private var persistenceSuspendDepth = 0
    private var contextMenu: ContextMenu? = null
    private var leftMouseDown = false
    private var rightMouseDown = false
    private var tabBarVisible = false
    private var mirroredLeftClickSequence = 0
    private var mirroredRightClickSequence = 0
    private var mirroredBlockClickActive = false
    private var mirroredLookState: MirroredLookState? = null
    private var mainInputMirroringActive = false
    private var lastMirroredConnectKey: String? = null
    private var lastMainWorldPresent = false
    private val lastAppliedLeftClickSequence = mutableMapOf<String, Int>()
    private val lastAppliedRightClickSequence = mutableMapOf<String, Int>()
    private val pendingMirroredReleaseTabs = mutableSetOf<String>()
    private val linkedBotStates = mutableMapOf<String, LinkedBotState>()

    private val activeTab: ClientTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    private val mainTab: ClientTab?
        get() = tabs.firstOrNull { it.isMain }

    fun currentTabId(): String? = activeTabId

    fun isMainTabActive(): Boolean = activeTab?.isMain == true

    fun sessionForTab(tabId: String): Session? =
        tabs.firstOrNull { it.id == tabId }?.sessionSnapshot?.toMinecraftSession()

    fun isTabSyncedToMain(tabId: String): Boolean =
        tabs.firstOrNull { it.id == tabId }?.syncedToMain == true

    fun shouldMirrorMainInputTo(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return mainInputMirroringActive && !tab.isMain && tab.syncedToMain && !shouldLinkBotControl(tabId)
    }

    fun clearLinkedBotRuntimeState() {
        linkedBotStates.values.forEach { it.navigator.clear() }
        linkedBotStates.clear()
    }

    fun applyMirroredLookState(tabId: String) {
        if (applyLinkedBotLookState(tabId)) {
            return
        }

        if (!shouldMirrorMainInputTo(tabId)) {
            return
        }

        val player = mc.thePlayer ?: return
        val lookState = mirroredLookState ?: return

        player.rotationYaw = lookState.rotationYaw
        player.rotationPitch = lookState.rotationPitch
        player.prevRotationYaw = lookState.rotationYaw
        player.prevRotationPitch = lookState.rotationPitch
        player.rotationYawHead = lookState.rotationYawHead
        player.prevRotationYawHead = lookState.rotationYawHead
        player.renderYawOffset = lookState.renderYawOffset
        player.prevRenderYawOffset = lookState.renderYawOffset
    }

    fun applyMirroredIngameActions(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return

        if (applyLinkedBotIngameActions(tabId)) {
            return
        }

        if (!shouldMirrorMainInputTo(tabId)) {
            primeMirroredInputState(tabId)
            if (tab.syncedToMain || pendingMirroredReleaseTabs.remove(tabId)) {
                releaseMirroredHeldActions()
            }
            return
        }

        pendingMirroredReleaseTabs.remove(tabId)

        if (lastAppliedLeftClickSequence[tabId] != mirroredLeftClickSequence) {
            if (mirroredLeftClickSequence > 0) {
                mc.clickMouse()
            }
            lastAppliedLeftClickSequence[tabId] = mirroredLeftClickSequence
        }

        if (lastAppliedRightClickSequence[tabId] != mirroredRightClickSequence) {
            if (mirroredRightClickSequence > 0) {
                mc.rightClickMouse()
            }
            lastAppliedRightClickSequence[tabId] = mirroredRightClickSequence
        }

        mc.sendClickBlockToController(mirroredBlockClickActive)
        syncMirroredUseItemState()
    }

    fun registerMirroredLeftClick() {
        if (!shouldRecordMirroredIngameInput()) {
            return
        }

        mirroredLeftClickSequence++
    }

    fun registerMirroredRightClick() {
        if (!shouldRecordMirroredIngameInput()) {
            return
        }

        mirroredRightClickSequence++
    }

    fun setMirroredBlockClick(active: Boolean) {
        if (!shouldRecordMirroredIngameInput()) {
            return
        }

        mirroredBlockClickActive = active
    }

    fun bootstrap() {
        if (bootstrapped) return

        val sessionSnapshot = SessionSnapshot.fromMinecraft() ?: SessionSnapshot.offline("Player")
        tabs += ClientTab(
            displayName = sessionSnapshot.username,
            sessionSnapshot = sessionSnapshot,
            configSnapshot = TabConfigSnapshot.captureCurrentState(),
            lastServerAddress = mc.currentServerData?.serverIP,
            isMain = true
        )
        activeTabId = tabs.firstOrNull()?.id
        bootstrapped = true
    }

    fun canPersistToMainStorage(): Boolean {
        if (!bootstrapped) {
            return true
        }

        return persistenceSuspendDepth == 0 && (activeTab?.isMain != false)
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
        bootstrap()

        val cleanUsername = username.trim()
        if (cleanUsername.isEmpty()) {
            return
        }

        val newTab = ClientTab(
            displayName = cleanUsername,
            sessionSnapshot = SessionSnapshot.offline(cleanUsername),
            configSnapshot = TabConfigSnapshot.fromSavedMainStorage()
        )

        tabs += newTab
        contextMenu = null
        switchToTab(newTab.id, returnScreen)
    }

    fun generateSuggestedUsername(
        mode: AltNameMode,
        prefix: String,
        length: Int,
        unformatted: Boolean
    ): String {
        return net.asd.union.utils.kotlin.RandomUtils.randomUsername(mode, prefix, length, unformatted)
    }

    fun defaultMode() = GuiClientConfiguration.altNameMode

    fun defaultPrefix() = GuiClientConfiguration.altsPrefix

    fun defaultLength() = GuiClientConfiguration.altsLength

    fun defaultUnformatted() = GuiClientConfiguration.unformattedAlts

    private fun mainRuntime(): LiveTabRuntime? {
        val currentMainTab = mainTab ?: return null
        return LiveTabRuntimeManager.runtimeFor(currentMainTab.id)
    }

    fun prepareDetachedKeyStates(runtime: LiveTabRuntime): Map<KeyBinding, Boolean>? {
        if (!shouldLinkBotControl(runtime.tabId)) {
            clearLinkedBotState(runtime.tabId)
            return null
        }

        val player = mc.thePlayer ?: run {
            clearLinkedBotState(runtime.tabId)
            return emptyMap()
        }
        val mainPlayer = mainRuntime()?.player ?: run {
            linkedBotState(runtime.tabId).apply {
                navigator.clear()
                followCommand = PathFollowCommand.idle()
                lookTarget = null
            }
            return emptyMap()
        }

        val state = linkedBotState(runtime.tabId)
        val mainFeet = BlockPos(mainPlayer.posX, mainPlayer.entityBoundingBox.minY, mainPlayer.posZ)
        val exactGoal = Vec3(mainPlayer.posX, mainPlayer.posY, mainPlayer.posZ)

        state.navigator.updateGoal(
            BaritoneGoalNear(mainFeet, LINK_BOT_FOLLOW_RANGE),
            exactGoal = exactGoal,
            successRadius = LINK_BOT_SUCCESS_RADIUS,
        )

        state.followCommand = state.navigator.tick(mc.theWorld, player)

        val linkedTarget = linkedBotSharedTarget()
        val attackFocusRange = linkedBotAttackRange() + LINK_BOT_FOCUS_PADDING

        state.lookTarget = when {
            linkedTarget != null && player.getDistanceToEntityBox(linkedTarget) <= attackFocusRange -> linkedTarget.hitBox.center
            state.followCommand.lookTarget != null -> {
                val lookTarget = state.followCommand.lookTarget!!
                Vec3(lookTarget.xCoord, player.posY + player.eyeHeight.toDouble(), lookTarget.zCoord)
            }

            else -> Vec3(mainPlayer.posX, player.posY + player.eyeHeight.toDouble(), mainPlayer.posZ)
        }

        return linkedBotPressedStates(state.followCommand)
    }

    private fun shouldLinkBotControl(tabId: String): Boolean {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return false
        return !tab.isMain && tab.syncedToMain && LinkBots.isLinkedControlActive() && mainRuntime()?.world != null
    }

    private fun linkedBotState(tabId: String): LinkedBotState {
        return linkedBotStates.getOrPut(tabId) {
            LinkedBotState(
                navigator = BaritoneNavigationSession(
                    navigationSettings = NavigationSettings(sprint = true)
                )
            )
        }
    }

    private fun clearLinkedBotState(tabId: String) {
        linkedBotStates.remove(tabId)?.navigator?.clear()
    }

    private fun pruneLinkedBotStates() {
        linkedBotStates.keys
            .filter { tabId -> tabs.none { it.id == tabId } || !shouldLinkBotControl(tabId) }
            .toList()
            .forEach(::clearLinkedBotState)
    }

    private fun linkedBotPressedStates(command: PathFollowCommand): Map<KeyBinding, Boolean> {
        return mapOf(
            mc.gameSettings.keyBindForward to (command.active && command.moveForward > 0f),
            mc.gameSettings.keyBindBack to (command.active && command.moveForward < 0f),
            mc.gameSettings.keyBindLeft to (command.active && command.moveStrafe < 0f),
            mc.gameSettings.keyBindRight to (command.active && command.moveStrafe > 0f),
            mc.gameSettings.keyBindJump to (command.active && command.jump),
            mc.gameSettings.keyBindSprint to (command.active && command.sprint),
        )
    }

    private fun applyLinkedBotLookState(tabId: String): Boolean {
        if (!shouldLinkBotControl(tabId)) {
            clearLinkedBotState(tabId)
            return false
        }

        val player = mc.thePlayer ?: return true
        val lookTarget = linkedBotStates[tabId]?.lookTarget ?: return true
        val rotation = toRotation(lookTarget, false, player)

        player.rotationYaw = rotation.yaw
        player.prevRotationYaw = rotation.yaw
        player.rotationPitch = rotation.pitch
        player.prevRotationPitch = rotation.pitch
        player.rotationYawHead = rotation.yaw
        player.prevRotationYawHead = rotation.yaw
        player.renderYawOffset = rotation.yaw
        player.prevRenderYawOffset = rotation.yaw

        return true
    }

    private fun applyLinkedBotIngameActions(tabId: String): Boolean {
        if (!shouldLinkBotControl(tabId)) {
            clearLinkedBotState(tabId)
            return false
        }

        val player = mc.thePlayer ?: return true
        val state = linkedBotStates[tabId] ?: return true

        releaseMirroredHeldActions()
        player setSprintSafely (state.followCommand.active && state.followCommand.sprint)
        tryLinkedBotAttack(state)

        return true
    }

    private fun tryLinkedBotAttack(state: LinkedBotState) {
        if (!KillAura.state) {
            return
        }

        val target = linkedBotSharedTarget() ?: return
        val player = mc.thePlayer ?: return
        val now = System.currentTimeMillis()

        if (now < state.nextAttackAt || !canLinkedBotAttack(player, target)) {
            return
        }

        val maxCps = (KillAura["MaxCPS"] as? IntegerValue)?.get() ?: 8
        if (maxCps <= 0) {
            return
        }

        if ((KillAura["SimulateCooldown"] as? BoolValue)?.get() == true && getAttackCooldownProgress() < 1f) {
            return
        }

        player.attackEntityWithModifiedSprint(target, false) {
            player.swingItem()
        }

        val minCps = (KillAura["MinCPS"] as? IntegerValue)?.get() ?: 5
        val safeMin = minOf(minCps, maxCps)
        val safeMax = maxOf(minCps, maxCps)
        state.nextAttackAt = now + randomClickDelay(safeMin, safeMax).toLong().coerceAtLeast(0L)
    }

    private fun canLinkedBotAttack(player: net.minecraft.client.entity.EntityPlayerSP, target: EntityLivingBase): Boolean {
        if (!target.isEntityAlive || target === player || player.isSpectator || player.isDead) {
            return false
        }

        if ((KillAura["OnSwording"] as? BoolValue)?.get() == true && player.heldItem?.item !is ItemSword) {
            return false
        }

        val attackRange = linkedBotAttackRange()
        val throughWallsRange = linkedBotThroughWallsRange()
        val distance = player.getDistanceToEntityBox(target)

        if (distance <= attackRange) {
            return true
        }

        return !player.canEntityBeSeen(target) && distance <= throughWallsRange
    }

    private fun linkedBotAttackRange(): Double {
        return (KillAura["Range"] as? FloatValue)?.get()?.toDouble() ?: 3.7
    }

    private fun linkedBotThroughWallsRange(): Double {
        return (KillAura["ThroughWallsRange"] as? FloatValue)?.get()?.toDouble() ?: linkedBotAttackRange()
    }

    private fun linkedBotSharedTarget(): EntityLivingBase? {
        if (!KillAuraTargeter.isLinkBotsModeActive()) {
            return null
        }

        return KillAuraTargeter.getTargetEntity()?.takeIf { it.isEntityAlive }
    }

    private fun sanitizedMirrorScreen(screen: GuiScreen?): GuiScreen? {
        return if (screen?.doesGuiPauseGame() == true) null else screen
    }

    private fun shouldRecordMirroredIngameInput(): Boolean {
        return !SessionRuntimeScope.isDetachedContextActive() &&
            activeTab?.isMain == true &&
            mc.currentScreen == null &&
            mc.theWorld != null &&
            mc.thePlayer != null
    }

    private fun refreshMirroredMainControlState() {
        mainInputMirroringActive = shouldRecordMirroredIngameInput()
        mirroredLookState = if (mainInputMirroringActive) {
            mc.thePlayer?.let { player ->
                MirroredLookState(
                    rotationYaw = player.rotationYaw,
                    rotationPitch = player.rotationPitch,
                    rotationYawHead = player.rotationYawHead,
                    renderYawOffset = player.renderYawOffset
                )
            }
        } else {
            null
        }

        if (!mainInputMirroringActive) {
            mirroredBlockClickActive = false
        }
    }

    private fun primeMirroredInputState(tabId: String) {
        lastAppliedLeftClickSequence[tabId] = mirroredLeftClickSequence
        lastAppliedRightClickSequence[tabId] = mirroredRightClickSequence
    }

    private fun clearMirroredInputState(tabId: String) {
        lastAppliedLeftClickSequence.remove(tabId)
        lastAppliedRightClickSequence.remove(tabId)
    }

    private fun releaseMirroredHeldActions() {
        runCatching {
            mc.sendClickBlockToController(false)
        }
        syncMirroredUseItemState(forceRelease = true)
    }

    private fun syncMirroredUseItemState(forceRelease: Boolean = false) {
        val player = mc.thePlayer ?: return
        val controller = mc.playerController ?: return
        val shouldHoldUseItem = !forceRelease && mc.gameSettings?.keyBindUseItem?.pressed == true

        if (!shouldHoldUseItem && player.isUsingItem) {
            runCatching {
                controller.onStoppedUsingItem(player)
            }
            runCatching {
                player.stopUsingItem()
            }
        }
    }

    private fun mainSourceScreen(): GuiScreen? {
        val currentMainTab = mainTab ?: return sanitizedMirrorScreen(mc.currentScreen)
        return if (currentMainTab.id == activeTabId) {
            sanitizedMirrorScreen(mc.currentScreen)
        } else {
            sanitizedMirrorScreen(mainRuntime()?.currentScreen)
        }
    }

    private fun mainSourceServerData(): ServerData? {
        val currentMainTab = mainTab ?: return mc.currentServerData
        return if (currentMainTab.id == activeTabId) {
            mc.currentServerData
        } else {
            mainRuntime()?.serverData
        }
    }

    private fun mainSourceWorldPresent(): Boolean {
        val currentMainTab = mainTab ?: return mc.theWorld != null
        return if (currentMainTab.id == activeTabId) {
            mc.theWorld != null
        } else {
            mainRuntime()?.world != null
        }
    }

    private fun mainSourceConfigSnapshot(): TabConfigSnapshot {
        val currentMainTab = mainTab
        return if (currentMainTab?.id == activeTabId) {
            TabConfigSnapshot.captureCurrentState()
        } else {
            currentMainTab?.configSnapshot ?: TabConfigSnapshot.captureCurrentState()
        }
    }

    private fun shouldRenderOn(screen: GuiScreen?): Boolean {
        bootstrap()

        return screen == null ||
            screen is GuiMainMenu ||
            screen is GuiMultiplayer ||
            screen is GuiConnecting ||
            screen is GuiDisconnected ||
            screen is GuiIngameMenu
    }

    private fun captureActiveTabState() {
        val tab = activeTab ?: return
        SessionSnapshot.fromMinecraft()?.let {
            tab.sessionSnapshot = it
            tab.displayName = it.username
        }
        tab.lastServerAddress = mc.currentServerData?.serverIP ?: tab.lastServerAddress
        tab.configSnapshot = TabConfigSnapshot.captureCurrentState()

        if (tab.isMain) {
            val syncedSnapshot = tab.configSnapshot
            tabs.filter { it.syncedToMain }.forEach { syncedTab ->
                syncedTab.configSnapshot = syncedSnapshot
                syncedTab.lastServerAddress = tab.lastServerAddress
            }
        }
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

        if (target.isMain && canPersistToMainStorage()) {
            FileManager.saveAllConfigs()
        }
    }

    private fun requestSwitch(tabId: String, returnScreen: GuiScreen?) {
        if (activeTabId == tabId) {
            return
        }

        switchToTab(tabId, returnScreen)
    }

    private fun switchToTab(tabId: String, returnScreen: GuiScreen?) {
        val previousActiveTabId = activeTabId
        val hadLiveWorld = mc.theWorld != null
        val capturedPreviousState = previousActiveTabId != null && LiveTabRuntimeManager.captureCurrentRuntime(previousActiveTabId)

        if (previousActiveTabId != null) {
            LiveTabRuntimeManager.prepareRuntimeForBackground(previousActiveTabId)
            primeMirroredInputState(previousActiveTabId)
            LiveTabRuntimeManager.deactivateRuntime(previousActiveTabId)
        }

        applyTab(tabId, captureCurrent = true)

        if (LiveTabRuntimeManager.activateRuntime(tabId)) {
            TabChatManager.restoreForTab(tabId)
            return
        }

        if (capturedPreviousState && hadLiveWorld) {
            val fallbackScreen = if (returnScreen == null || returnScreen is GuiIngameMenu) {
                GuiMainMenu()
            } else {
                returnScreen
            }
            LiveTabRuntimeManager.clearMinecraftRuntime(fallbackScreen)
            TabChatManager.restoreForTab(tabId)
            return
        }

        mc.displayGuiScreen(returnScreen)
        TabChatManager.restoreForTab(tabId)
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
        val fallback = tabs.firstOrNull { it.id != tabId && it.isMain }
            ?: tabs.firstOrNull { it.id != tabId }

        tabs.remove(tabToRemove)
        clearMirroredInputState(tabId)
        pendingMirroredReleaseTabs.remove(tabId)
        clearLinkedBotState(tabId)
        contextMenu = null

        if (tabs.none { it.isMain }) {
            (fallback ?: tabs.firstOrNull())?.isMain = true
        }

        if (!wasActive) {
            TabChatManager.clearTab(tabId)
            LiveTabRuntimeManager.removeRuntime(tabId)
            if (activeTab?.isMain == true && canPersistToMainStorage()) {
                FileManager.saveAllConfigs()
            }
            mc.displayGuiScreen(returnScreen)
            return
        }

        if (mc.theWorld != null && activeTabId != null) {
            LiveTabRuntimeManager.captureCurrentRuntime(activeTabId!!)
            LiveTabRuntimeManager.deactivateRuntime(activeTabId)
        }

        TabChatManager.clearTab(tabId)
        LiveTabRuntimeManager.removeRuntime(tabId)

        if (fallback != null) {
            applyTab(fallback.id, captureCurrent = false)
            if (!LiveTabRuntimeManager.activateRuntime(fallback.id)) {
                LiveTabRuntimeManager.clearMinecraftRuntime(GuiMainMenu())
            }
            TabChatManager.restoreForTab(fallback.id)
        } else {
            activeTabId = null
            LiveTabRuntimeManager.clearMinecraftRuntime(GuiMainMenu())
            TabChatManager.restoreForTab(null)
        }
    }

    private fun setMainTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        tabs.forEach {
            it.isMain = it.id == tabId
            if (it.isMain) {
                it.syncedToMain = false
            }
        }
        contextMenu = null

        if (target.id == activeTabId && canPersistToMainStorage()) {
            FileManager.saveAllConfigs()
            ClientUtils.displayAlert("${target.displayName} is now the main tab.")
        } else {
            ClientUtils.displayAlert("${target.displayName} will save to main storage when it becomes active.")
        }
    }

    private fun openAddTabScreen(returnScreen: GuiScreen?) {
        mc.displayGuiScreen(GuiAddClientTab(returnScreen))
    }

    private fun toggleSyncWithMain(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (target.isMain) {
            return
        }

        target.syncedToMain = !target.syncedToMain
        contextMenu = null

        if (target.syncedToMain) {
            primeMirroredInputState(target.id)
            pendingMirroredReleaseTabs.remove(target.id)
            val sourceScreen = mainSourceScreen()
            val sourceServerData = mainSourceServerData()
            target.configSnapshot = mainSourceConfigSnapshot()
            target.lastServerAddress = sourceServerData?.serverIP ?: mainTab?.lastServerAddress ?: target.lastServerAddress
            LiveTabRuntimeManager.mirrorCurrentScreen(
                target.id,
                sourceScreen,
                sourceServerData
            )
            ClientUtils.displayAlert("${target.displayName} is now synced with the main tab.")
        } else {
            pendingMirroredReleaseTabs += target.id
            clearMirroredInputState(target.id)
            clearLinkedBotState(target.id)
            ClientUtils.displayAlert("${target.displayName} is no longer synced with the main tab.")
        }
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
            val fillColor = when {
                active -> Color(accent.red, accent.green, accent.blue, 185).rgb
                hovered -> Color(55, 55, 64, 220).rgb
                else -> Color(34, 34, 42, 200).rgb
            }
            val borderColor = if (tabLayout.isMain) {
                Color(236, 185, 84, 220).rgb
            } else {
                Color(0, 0, 0, 80).rgb
            }

            Gui.drawRect(tabLayout.x, BAR_Y + 2, tabLayout.x + tabLayout.width, BAR_Y + BAR_HEIGHT - 2, fillColor)
            Gui.drawRect(tabLayout.x, BAR_Y + 2, tabLayout.x + tabLayout.width, BAR_Y + 3, borderColor)

            val labelColor = if (tabLayout.syncedToMain && !tabLayout.isMain) {
                Color(236, 185, 84).rgb
            } else {
                0xFFFFFF
            }
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

        contextMenu?.let { menu ->
            val menuTab = tabs.firstOrNull { it.id == menu.tabId } ?: return@let
            if (menuTab.isMain) return@let

            Gui.drawRect(menu.x, menu.y, menu.x + MENU_WIDTH, menu.y + MENU_HEIGHT, Color(24, 24, 29, 235).rgb)
            Gui.drawRect(menu.x, menu.y, menu.x + MENU_WIDTH, menu.y + 1, Color(accent.red, accent.green, accent.blue, 180).rgb)
            font.drawStringWithShadow("Set As Main Tab", (menu.x + 8).toFloat(), (menu.y + 6).toFloat(), 0xFFFFFF)
            font.drawStringWithShadow(
                if (menuTab.syncedToMain) "Stop Sync With Main" else "Sync With Main",
                (menu.x + 8).toFloat(),
                (menu.y + MENU_ITEM_HEIGHT + 6).toFloat(),
                0xFFFFFF
            )
        }
    }

    private fun handleMouseClick(returnScreen: GuiScreen?, mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        val layout = buildLayout(ScaledResolution(mc).scaledWidth)
        val inBar = mouseY in BAR_Y..(BAR_Y + BAR_HEIGHT)

        contextMenu?.let { menu ->
            val inMenu = mouseX in menu.x..(menu.x + MENU_WIDTH) && mouseY in menu.y..(menu.y + MENU_HEIGHT)
            if (inMenu) {
                if (mouseButton == 0) {
                    val menuIndex = ((mouseY - menu.y) / MENU_ITEM_HEIGHT).coerceIn(0, 1)
                    when (menuIndex) {
                        0 -> setMainTab(menu.tabId)
                        1 -> toggleSyncWithMain(menu.tabId)
                    }
                }
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

                1 -> {
                    contextMenu = if (clickedTab.isMain) {
                        null
                    } else {
                        ContextMenu(
                            clickedTab.id,
                            mouseX.coerceAtMost(ScaledResolution(mc).scaledWidth - MENU_WIDTH - 4),
                            (BAR_Y + BAR_HEIGHT + 4).coerceAtLeast(mouseY)
                        )
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
            val label = ellipsize(if (tab.isMain) "[M] ${tab.displayName}" else tab.displayName, 22)
            val width = (font.getStringWidth(label) + TAB_PADDING * 2 + 10).coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)

            if (x + width > maxRight) {
                return@forEach
            }

            layouts += TabLayout(
                id = tab.id,
                label = label,
                x = x,
                width = width,
                isMain = tab.isMain,
                syncedToMain = tab.syncedToMain
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
        if (mc.currentScreen != null || !shouldRenderOn(null) || !tabBarVisible) {
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
        activeTab?.let { tab ->
            mc.currentServerData?.serverIP?.let { tab.lastServerAddress = it }
        }
        LiveTabRuntimeManager.syncActiveRuntime(activeTabId)
        refreshMirroredMainControlState()
        pruneLinkedBotStates()
        activeTabId?.let { primeMirroredInputState(it) }
        LiveTabRuntimeManager.tickBackgroundRuntimes(activeTabId)
        syncTabsWithMainController()
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
        var isMain: Boolean = false,
        var syncedToMain: Boolean = false
    )

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
                return SessionSnapshot(session.username, session.playerID, session.token, session.sessionType.name.lowercase())
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
        val width: Int,
        val isMain: Boolean,
        val syncedToMain: Boolean
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

    private fun syncedTabsForMainControl() = tabs.filter { it.syncedToMain && !it.isMain }

    private fun syncTabsWithMainController() {
        val syncedTabs = syncedTabsForMainControl()
        val sourceScreen = mainSourceScreen()
        val sourceServerData = mainSourceServerData()
        val sourceWorldPresent = mainSourceWorldPresent()

        if (syncedTabs.isEmpty()) {
            lastMirroredConnectKey = null
            lastMainWorldPresent = sourceWorldPresent
            return
        }

        syncedTabs.forEach { tab ->
            LiveTabRuntimeManager.mirrorCurrentScreen(tab.id, sourceScreen, sourceServerData)
        }

        val connectKey = if (sourceScreen is GuiConnecting && sourceServerData != null) {
            "${sourceServerData.serverIP}#${System.identityHashCode(sourceScreen)}"
        } else {
            null
        }

        if (connectKey != null && connectKey != lastMirroredConnectKey) {
            val sourceServerIp = sourceServerData?.serverIP ?: return
            syncedTabs.forEach { tab ->
                LiveTabRuntimeManager.connectRuntime(
                    tab.id,
                    tab.sessionSnapshot.toMinecraftSession(),
                    ServerData("", sourceServerIp, false),
                    sourceScreen
                )
            }
        }

        if (lastMainWorldPresent && !sourceWorldPresent && sourceScreen !is GuiConnecting) {
            syncedTabs.forEach { tab ->
                LiveTabRuntimeManager.disconnectRuntimeToScreen(tab.id, "Mirrored main disconnect", sourceScreen)
            }
        }

        lastMirroredConnectKey = connectKey
        lastMainWorldPresent = sourceWorldPresent
    }

    private data class MirroredLookState(
        val rotationYaw: Float,
        val rotationPitch: Float,
        val rotationYawHead: Float,
        val renderYawOffset: Float
    )

    private data class LinkedBotState(
        val navigator: BaritoneNavigationSession,
        var followCommand: PathFollowCommand = PathFollowCommand.idle(),
        var lookTarget: Vec3? = null,
        var nextAttackAt: Long = 0L
    )
}
