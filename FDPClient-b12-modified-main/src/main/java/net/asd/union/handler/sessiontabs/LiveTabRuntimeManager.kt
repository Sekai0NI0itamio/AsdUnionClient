package net.asd.union.handler.sessiontabs

import net.asd.union.handler.payload.ClientFixes
import net.asd.union.injection.forge.mixins.gui.MixinGuiConnectingAccessor
import net.asd.union.ui.client.gui.GuiMainMenu
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.inventory.InventoryUtils
import net.asd.union.utils.performance.ChunkOptimizer
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.PlayerControllerMP
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.network.NetHandlerPlayClient
import net.minecraft.entity.Entity
import net.minecraft.network.INetHandler
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import net.minecraft.network.play.server.S3FPacketCustomPayload
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Session
import java.util.concurrent.ConcurrentLinkedQueue

// ──────────────────────────────────────────────────────────────────────────────
// Helper functions (shared between SessionRuntimeScope and LiveTabRuntimeManager)
// ──────────────────────────────────────────────────────────────────────────────

internal fun sanitizeScreen(screen: GuiScreen?): GuiScreen? {
    return if (screen?.doesGuiPauseGame() == true) null else screen
}

internal fun sanitizeRuntimeRenderViewEntity(
    world: WorldClient?,
    player: EntityPlayerSP?,
    renderViewEntity: Entity?
): Entity? {
    if (world == null || player == null) return null
    if (renderViewEntity == null || renderViewEntity.isDead || renderViewEntity.worldObj !== world) return player
    return renderViewEntity
}

// ──────────────────────────────────────────────────────────────────────────────
// SessionRuntimeScope — simplified: only used for detached-context detection
// during initial connection (connectRuntime). No more state-swapping for
// background ticking — that's handled by TabSimulationThread now.
// ──────────────────────────────────────────────────────────────────────────────

object SessionRuntimeScope : MinecraftInstance {

    private val detachedRuntime = ThreadLocal<LiveTabRuntime?>()

    fun isDetachedContextActive() = detachedRuntime.get() != null

    fun currentRuntime(): LiveTabRuntime? = detachedRuntime.get()

    fun updateDetachedScreen(screen: GuiScreen?) {
        detachedRuntime.get()?.currentScreen = sanitizeScreen(screen)
    }

    /**
     * Run a block with the given runtime set as the "detached" context.
     * This is ONLY used for initial connection setup (connectRuntime) where
     * we need to temporarily set mc state so vanilla connection code works.
     *
     * After the block, the mc state is restored and the runtime keeps its
     * own references independently.
     */
    fun <T> runDetached(
        runtime: LiveTabRuntime,
        clearScreen: Boolean = true,
        block: () -> T
    ): T {
        val previousRuntime = detachedRuntime.get()
        detachedRuntime.set(runtime)

        // Snapshot current mc state
        val savedSession = mc.session
        val savedWorld = mc.theWorld
        val savedPlayer = mc.thePlayer
        val savedController = mc.playerController
        val savedRenderView = mc.renderViewEntity
        val savedServerData = mc.currentServerData

        return try {
            // Apply runtime state to mc for vanilla code
            mc.session = runtime.session
            mc.theWorld = runtime.world
            mc.thePlayer = runtime.player
            mc.playerController = runtime.playerController
            mc.renderViewEntity = runtime.renderViewEntity
            mc.setServerData(runtime.serverData)
            if (clearScreen) {
                mc.currentScreen = null
            }

            block()
        } finally {
            // Sync back any changes to the runtime
            runtime.session = mc.session
            runtime.world = mc.theWorld
            runtime.player = mc.thePlayer
            runtime.playerController = mc.playerController
            runtime.serverData = mc.currentServerData
            runtime.renderViewEntity = sanitizeRuntimeRenderViewEntity(mc.theWorld, mc.thePlayer, mc.renderViewEntity)
            runtime.currentScreen = sanitizeScreen(mc.currentScreen)
            runtime.backgroundNetworkManager = mc.thePlayer?.sendQueue?.networkManager ?: runtime.backgroundNetworkManager
            runtime.currentHandler = (mc.thePlayer?.sendQueue as? INetHandler) ?: runtime.currentHandler
            runtime.normalizeRenderState()

            // Restore mc state
            mc.session = savedSession
            mc.theWorld = savedWorld
            mc.thePlayer = savedPlayer
            mc.playerController = savedController
            mc.renderViewEntity = savedRenderView
            mc.setServerData(savedServerData)

            if (previousRuntime != null) {
                detachedRuntime.set(previousRuntime)
            } else {
                detachedRuntime.remove()
            }
        }
    }

    fun resetSharedActionStateForCurrentPlayer() {
        // No-op: rotation/action state is now per-tab via the simulation thread
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// LiveTabRuntimeManager — manages per-tab runtimes and simulation threads
// ──────────────────────────────────────────────────────────────────────────────

object LiveTabRuntimeManager : MinecraftInstance {

    private val runtimes = linkedMapOf<String, LiveTabRuntime>()
    @Volatile
    private var activeRuntimeTabId: String? = null

    /** Public accessor for the active runtime tab ID, used by mixins. */
    fun getActiveRuntimeTabId(): String? = activeRuntimeTabId

    /**
     * Direct mapping from NetworkManager instance to runtime tab ID.
     * This is the MOST RELIABLE way to route packets because:
     * - Each NetworkManager belongs to exactly one connection/tab
     * - The mapping is set when the NM is created and never changes
     * - It doesn't depend on handler references which can become stale
     *
     * This mapping is maintained in:
     * - connectRuntime() — when a new tab's NM is created
     * - registerDetachedJoin() — when the login→play transition happens
     * - captureCurrentRuntime() — when a tab goes to background
     * - removeRuntime() — when a tab is removed
     */
    private val nmToTabId = java.util.concurrent.ConcurrentHashMap<NetworkManager, String>()

    private fun logRuntimeInfo(runtime: LiveTabRuntime, message: String) {
        ClientUtils.LOGGER.info("[TabRuntime][${runtime.debugLabel()}] $message")
    }

    private fun logRuntimeWarn(runtime: LiveTabRuntime, message: String) {
        ClientUtils.LOGGER.warn("[TabRuntime][${runtime.debugLabel()}] $message")
    }

    // ── Query ──────────────────────────────────────────────────────────────

    fun hasRuntime(tabId: String) = runtimes[tabId]?.hasRestorableState == true

    fun runtimeFor(tabId: String): LiveTabRuntime? = runtimes[tabId]

    fun hasLiveConnections(): Boolean {
        val activeServerData = mc.currentServerData
        val activeNetworkManager = mc.thePlayer?.sendQueue?.networkManager

        if (activeServerData != null && (
            (mc.theWorld != null && mc.thePlayer != null) ||
                mc.currentScreen is net.minecraft.client.multiplayer.GuiConnecting ||
                activeNetworkManager?.isChannelOpen == true
        )) {
            return true
        }

        return runtimes.values.any { runtime ->
            runtime.serverData != null && (
                runtime.hasWorldState ||
                    runtime.currentScreen is net.minecraft.client.multiplayer.GuiConnecting ||
                    runtime.networkManager?.isChannelOpen == true
                )
        }
    }

    // ── Active tab management ──────────────────────────────────────────────

    fun deactivateRuntime(tabId: String?) {
        if (activeRuntimeTabId == tabId) {
            activeRuntimeTabId = null
        }
    }

    /**
     * Capture the current mc state into a runtime and start its simulation thread.
     * Called when a tab goes from active → background.
     */
    fun captureCurrentRuntime(tabId: String): Boolean {
        val runtime = runtimes.getOrPut(tabId) { LiveTabRuntime(tabId) }
        ClientUtils.LOGGER.info("[TabRuntime][capture] Capturing mc state for tab=$tabId: " +
            "world=${mc.theWorld != null}, player=${mc.thePlayer != null}, " +
            "controller=${mc.playerController != null}, screen=${mc.currentScreen?.javaClass?.simpleName}")
        runtime.session = mc.session
        runtime.world = mc.theWorld
        runtime.player = mc.thePlayer
        runtime.playerController = mc.playerController
        runtime.serverData = mc.currentServerData
        runtime.renderViewEntity = sanitizeRuntimeRenderViewEntity(mc.theWorld, mc.thePlayer, mc.renderViewEntity)
        runtime.currentScreen = sanitizeScreen(mc.currentScreen)
        runtime.backgroundNetworkManager = mc.thePlayer?.sendQueue?.networkManager ?: runtime.backgroundNetworkManager
        runtime.currentHandler = (mc.thePlayer?.sendQueue as? INetHandler) ?: runtime.currentHandler
        runtime.disconnectedReason = null
        runtime.connected = runtime.currentHandler != null
        runtime.normalizeRenderState()
        runtime.refreshDebugName()

        ClientUtils.LOGGER.info("[TabRuntime][capture] Tab=$tabId state captured: " +
            "hasRestorableState=${runtime.hasRestorableState}, connected=${runtime.connected}, " +
            "bgNM=${runtime.backgroundNetworkManager != null}, handler=${runtime.currentHandler != null}")

        // Register the NM mapping for reliable packet routing
        // Only register if not already registered (idempotent)
        val bgNm = runtime.backgroundNetworkManager
        val netNm = runtime.networkManager
        if (bgNm != null) {
            val existing = nmToTabId[bgNm]
            if (existing == null) {
                nmToTabId[bgNm] = tabId
            }
        }
        if (netNm != null) {
            val existing = nmToTabId[netNm]
            if (existing == null) {
                nmToTabId[netNm] = tabId
            }
        }

        // Start the simulation thread for this background tab
        startSimulationThread(runtime)

        return runtime.hasRestorableState
    }

    fun syncActiveRuntime(tabId: String?) {
        if (tabId == null) return
        // Create the runtime if it doesn't exist yet. This is critical because
        // the active tab's runtime is only created lazily (in captureCurrentRuntime
        // when the tab goes to background). Without creating it here, activeRuntimeTabId
        // stays null and the NM mapping is never populated for the active tab.
        val runtime = runtimes.getOrPut(tabId) { LiveTabRuntime(tabId) }

        // If the active tab is in a "connecting" state (mc state was cleared by
        // activateRuntime but the sim thread is still running), check if the sim
        // thread has created a world/player. If so, restore them to mc so the
        // user sees the game world instead of the "Connecting..." screen.
        if (mc.theWorld == null && mc.thePlayer == null && runtime.hasWorldState) {
            logRuntimeInfo(runtime, "Sim thread created world while tab is active — restoring to mc")
            // The sim thread has created a world/player. Restore them to mc.
            // Stop the sim thread first — the main thread takes over.
            stopSimulationThread(runtime)

            // Drain pending packets from the sim thread
            val simThread = runtime.simulationThread
            if (simThread != null) {
                val pendingCount = simThread.getPendingPacketCount()
                if (pendingCount > 0) {
                    logRuntimeInfo(runtime, "Draining $pendingCount pending packets from simulation thread during sync restore")
                    TabSimulationThread.setCurrentProcessingRuntime(runtime)
                    try {
                        simThread.drainAllPackets()
                    } finally {
                        TabSimulationThread.clearCurrentProcessingRuntime()
                    }
                }
            }
            runtime.drainQueuedTasks()

            // Restore mc state from the runtime
            mc.session = runtime.session
            mc.theWorld = runtime.world
            mc.thePlayer = runtime.player
            mc.playerController = runtime.playerController
            mc.renderViewEntity = runtime.renderViewEntity ?: runtime.player
            mc.objectMouseOver = null
            mc.pointedEntity = null
            mc.setServerData(runtime.serverData)

            // Create a fresh PlayerControllerMP with the correct handler
            val activePlayer = runtime.player
            val activeController = runtime.playerController
            val activeHandler = if (activePlayer != null) activePlayer.sendQueue as? INetHandler else null
            if (activeHandler is net.minecraft.client.network.NetHandlerPlayClient && activePlayer != null) {
                val gameType = activeController?.currentGameType
                    ?: net.minecraft.world.WorldSettings.GameType.SURVIVAL
                val newController = net.minecraft.client.multiplayer.PlayerControllerMP(mc, activeHandler)
                newController.setGameType(gameType)
                mc.playerController = newController
                runtime.playerController = newController
            }

            // Update the NetHandlerPlayClient's clientWorldController
            val handler = activeHandler ?: runtime.currentHandler
            if (handler is net.minecraft.client.network.NetHandlerPlayClient) {
                runCatching {
                    var clazz: Class<*>? = handler.javaClass
                    while (clazz != null) {
                        for (fieldName in arrayOf("field_147300_g", "clientWorldController")) {
                            try {
                                val worldField = clazz.getDeclaredField(fieldName)
                                worldField.isAccessible = true
                                worldField.set(handler, runtime.world)
                                break
                            } catch (_: NoSuchFieldException) { /* try next */ }
                        }
                        clazz = clazz.superclass
                    }
                }
                val handlerNm = handler.networkManager
                if (handlerNm != null && nmToTabId.getOrDefault(handlerNm, null) == null) {
                    nmToTabId[handlerNm] = tabId
                }
            }

            runtime.currentHandler = activeHandler ?: runtime.currentHandler
            runtime.backgroundNetworkManager = activePlayer?.sendQueue?.networkManager ?: runtime.backgroundNetworkManager

            // Rebuild the renderer
            runCatching {
                ChunkOptimizer.skipNextLoadRenderers = false
                mc.renderGlobal.setWorldAndLoadRenderers(runtime.world)
            }
            runCatching { compilePlayerChunkColumn() }

            // Clear the "Connecting..." screen
            mc.displayGuiScreen(null)
            runtime.currentScreen = null

            logRuntimeInfo(runtime, "Restored world state from sim thread to mc")
            return
        }

        runtime.session = mc.session
        runtime.world = mc.theWorld
        runtime.player = mc.thePlayer
        runtime.playerController = mc.playerController
        runtime.serverData = mc.currentServerData
        runtime.renderViewEntity = sanitizeRuntimeRenderViewEntity(mc.theWorld, mc.thePlayer, mc.renderViewEntity)
        runtime.currentScreen = sanitizeScreen(mc.currentScreen)
        val newNm = mc.thePlayer?.sendQueue?.networkManager
        // If the NM changed (e.g. reconnected to a different server), clean up
        // the old NM mapping before registering the new one
        if (newNm != null && newNm !== runtime.backgroundNetworkManager) {
            runtime.backgroundNetworkManager?.let { oldNm ->
                if (nmToTabId[oldNm] == tabId) nmToTabId.remove(oldNm)
            }
            runtime.networkManager?.let { oldNm ->
                if (nmToTabId[oldNm] == tabId) nmToTabId.remove(oldNm)
            }
        }
        runtime.backgroundNetworkManager = newNm ?: runtime.backgroundNetworkManager
        runtime.currentHandler = (mc.thePlayer?.sendQueue as? INetHandler) ?: runtime.currentHandler
        runtime.disconnectedReason = null
        runtime.connected = runtime.currentHandler != null
        runtime.normalizeRenderState()
        runtime.refreshDebugName()

        // Register the active tab's NM in nmToTabId so that when this tab
        // goes to background, packets can be immediately routed correctly.
        // Without this, nmToTabId is only populated in captureCurrentRuntime,
        // which runs AFTER the tab switch — leaving a gap where packets arrive
        // on the NM but can't be routed to any runtime.
        // Only register if not already registered (idempotent)
        val bgNm = runtime.backgroundNetworkManager
        val netNm = runtime.networkManager
        if (bgNm != null) {
            val existing = nmToTabId[bgNm]
            if (existing == null) {
                nmToTabId[bgNm] = tabId
            }
        }
        if (netNm != null) {
            val existing = nmToTabId[netNm]
            if (existing == null) {
                nmToTabId[netNm] = tabId
            }
        }

        // Always set activeRuntimeTabId for the active tab, even if it doesn't
        // have restorable state yet (e.g., still connecting, in main menu).
        // This is critical for isHandlerForBackgroundTab and enqueueIncomingPacket
        // to correctly distinguish active vs background tabs.
        activeRuntimeTabId = tabId

        // Only remove the runtime if it's truly dead (disconnected with no
        // chance of recovery). Don't remove just because it has no world yet
        // (the player might be connecting).
        if (!runtime.hasRestorableState && runtime.disconnectedReason != null) {
            // Clean up NM mapping before removing
            val bgNm = runtime.backgroundNetworkManager
            val netNm = runtime.networkManager
            if (bgNm != null && nmToTabId[bgNm] == tabId) {
                nmToTabId.remove(bgNm)
            }
            if (netNm != null && nmToTabId[netNm] == tabId) {
                nmToTabId.remove(netNm)
            }
            runtimes.remove(tabId)
        }
    }

    fun prepareRuntimeForBackground(tabId: String) {
        val runtime = runtimes[tabId] ?: return
        runtime.currentScreen = sanitizeScreen(runtime.currentScreen)
    }

    /**
     * Activate a runtime: stop its simulation thread and set its state on mc
     * for the main thread to render/tick.
     */
    fun activateRuntime(tabId: String): Boolean {
        val runtime = runtimes[tabId] ?: return false
        runtime.normalizeRenderState()
        if (!runtime.hasRestorableState) {
            System.out.println("[TabDebug][activateRuntime] ENTER no-restorable-state path for tab=$tabId")
            System.out.println("[TabDebug][activateRuntime] runtime state: world=${runtime.world != null}, player=${runtime.player != null}, " +
                "controller=${runtime.playerController != null}, connected=${runtime.connected}, " +
                "disconnectedReason=${runtime.disconnectedReason}, hasWorldState=${runtime.hasWorldState}")
            System.out.println("[TabDebug][activateRuntime] mc state BEFORE: theWorld=${mc.theWorld != null}, " +
                "thePlayer=${mc.thePlayer != null}, controller=${mc.playerController != null}, " +
                "currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
            System.out.flush()

            activeRuntimeTabId = tabId
            System.out.println("[TabDebug][activateRuntime] STEP 1: Set activeRuntimeTabId=$tabId")
            System.out.flush()

            // If the tab is still connecting (NM open, no world yet), keep the
            // simulation thread running — it's processing login packets. Only stop
            // it if the tab is disconnected (no point keeping it running).
            // We check both the connected flag AND the network channel status
            // because the connected flag can be stale/reset by race conditions
            // even when the channel is actually still open (e.g., during the
            // login phase when the simulation thread briefly sees an
            // uninitialized NM). The channel is the source of truth.
            val activeNm = runtime.networkManager ?: runtime.backgroundNetworkManager
            val channelOpen = activeNm?.isChannelOpen == true
            val isStillConnecting = (runtime.connected && runtime.disconnectedReason == null) ||
                (channelOpen && runtime.disconnectedReason == null)
            System.out.println("[TabDebug][activateRuntime] STEP 2: isStillConnecting=$isStillConnecting " +
                "(connected=${runtime.connected}, channelOpen=$channelOpen, " +
                "disconnectedReason=${runtime.disconnectedReason})")
            System.out.flush()

            if (!isStillConnecting) {
                stopSimulationThread(runtime)
                System.out.println("[TabDebug][activateRuntime] STEP 2a: Stopped simulation thread")
                System.out.flush()
            }

            // CRITICAL: Display the appropriate screen BEFORE clearing mc state.
            val screenToShow: GuiScreen? = if (isStillConnecting) {
                logRuntimeInfo(runtime, "Activating tab during login phase (no world yet)")
                runtime.currentScreen ?: run {
                    val serverData = runtime.serverData
                    if (serverData != null) {
                        GuiDisconnected(
                            GuiMultiplayer(net.asd.union.ui.client.gui.GuiMainMenu()),
                            "connect.connecting",
                            net.minecraft.util.ChatComponentText("Connecting to ${serverData.serverIP}...")
                        )
                    } else {
                        GuiMultiplayer(net.asd.union.ui.client.gui.GuiMainMenu())
                    }
                }
            } else {
                logRuntimeWarn(runtime, "Cannot activate: no restorable state (world=${runtime.world != null}, player=${runtime.player != null})")
                runtime.currentScreen ?: net.minecraft.client.gui.GuiMainMenu()
            }
            System.out.println("[TabDebug][activateRuntime] STEP 3: screenToShow=${screenToShow?.javaClass?.simpleName}")
            System.out.flush()

            // Now safely clear the mc singleton's world/player state.
            val mcAccessor = mc as net.asd.union.injection.forge.mixins.client.MixinMinecraftAccessor
            val prevNm = mcAccessor.getMyNetworkManager()
            System.out.println("[TabDebug][activateRuntime] STEP 4: About to clear mc state. " +
                "prevNM=${prevNm != null}, prevWorld=${mc.theWorld != null}, " +
                "prevPlayer=${mc.thePlayer != null}, prevController=${mc.playerController != null}")
            System.out.flush()

            System.out.println("[TabDebug][activateRuntime] STEP 4a: Calling mc.displayGuiScreen($screenToShow)")
            System.out.flush()
            mc.displayGuiScreen(screenToShow)
            System.out.println("[TabDebug][activateRuntime] STEP 4b: displayGuiScreen done. " +
                "currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
            System.out.flush()

            // CRITICAL: Defer the state clearing to the next tick's HEAD.
            // If we null thePlayer/theWorld here, the rest of the current runTick
            // will NPE on thePlayer access (keybind processing block, etc.).
            // By deferring, the current tick finishes with the old (valid) player,
            // and the next tick's HEAD guard handles the null state safely.
            val capturedIsStillConnecting = isStillConnecting
            val capturedPrevNm = prevNm
            TabSwitchState.pendingClear = Runnable {
                mc.theWorld = null
                mc.thePlayer = null
                mc.playerController = null
                mc.renderViewEntity = null
                mc.objectMouseOver = null
                mc.pointedEntity = null
                mc.setServerData(null)
                if (!capturedIsStillConnecting && capturedPrevNm != null) {
                    mcAccessor.setMyNetworkManager(null)
                }
                runCatching { mc.renderGlobal.setWorldAndLoadRenderers(null) }
                System.out.println("[TabDebug][activateRuntime] DEFERRED CLEAR DONE. " +
                    "theWorld=${mc.theWorld != null}, thePlayer=${mc.thePlayer != null}, " +
                    "controller=${mc.playerController != null}")
                System.out.flush()
            }
            System.out.println("[TabDebug][activateRuntime] STEP 4c: State clear DEFERRED to next tick")
            System.out.flush()

            System.out.println("[TabDebug][activateRuntime] STEP 7: DONE (deferred). Final mc state: " +
                "NM=${mcAccessor.getMyNetworkManager() != null}, " +
                "world=${mc.theWorld != null}, player=${mc.thePlayer != null}, " +
                "controller=${mc.playerController != null}, screen=${mc.currentScreen?.javaClass?.simpleName}")
            System.out.flush()

            return true
        }

        // IMPORTANT: Set activeRuntimeTabId BEFORE draining tasks and processing
        // packets. If we set it after, then during processReceivedPackets() the
        // isHandlerForBackgroundTab() check would incorrectly classify this tab's
        // handler as a "background" handler (because activeRuntimeTabId still points
        // to the previous tab), causing fdp$handleRespawnForSimThread to CANCEL
        // S07PacketRespawn packets that should be processed normally on the main thread.
        activeRuntimeTabId = tabId

        // Stop the simulation thread — the main thread takes over
        stopSimulationThread(runtime)

        // CRITICAL: Drain the simulation thread's packet queue before
        // restoring state. Any packets that were enqueued but not yet
        // processed by the simulation thread must be processed now,
        // otherwise they're lost (entity positions, spawn packets, etc.)
        val simThread = runtime.simulationThread
        if (simThread != null) {
            val pendingCount = simThread.getPendingPacketCount()
            if (pendingCount > 0) {
                logRuntimeInfo(runtime, "Draining $pendingCount pending packets from simulation thread during activation")
                // Set the ThreadLocal context so Mixin redirects return this tab's state
                TabSimulationThread.setCurrentProcessingRuntime(runtime)
                try {
                    simThread.drainAllPackets()
                } finally {
                    TabSimulationThread.clearCurrentProcessingRuntime()
                }
            }
        }

        // Drain any remaining queued tasks from the simulation thread.
        // Do NOT call processReceivedPackets() here — the mc state hasn't
        // been restored yet, so packets processed during that call would
        // see stale mc.thePlayer/mc.theWorld values, causing state corruption.
        // Packets will be processed on the next tick after the state is restored.
        runtime.drainQueuedTasks()

        // Restore mc state from the runtime
        mc.session = runtime.session
        mc.theWorld = runtime.world
        mc.thePlayer = runtime.player
        mc.playerController = runtime.playerController
        // Ensure renderViewEntity is never null — fall back to the player
        mc.renderViewEntity = runtime.renderViewEntity ?: runtime.player
        mc.objectMouseOver = null
        mc.pointedEntity = null
        mc.setServerData(runtime.serverData)

        // CRITICAL: Ensure the player's sendQueue and the playerController's
        // netClientHandler both point to the same NetHandlerPlayClient, and
        // that this handler's networkManager is the correct NM for this tab.
        // If they diverge (e.g., after a respawn on the main thread that
        // wasn't captured by syncActiveRuntime), actions like block placement
        // and digging would go through the wrong handler/NM, causing desync.
        //
        // Rather than trying to verify via reflection (which is fragile due to
        // field name differences between SRG/MCP/Notch at runtime), we ALWAYS
        // create a new PlayerControllerMP with the correct handler. This
        // guarantees the controller's netClientHandler is correct.
        val activePlayer = runtime.player
        val activeController = runtime.playerController
        val activeHandler = if (activePlayer != null) activePlayer.sendQueue as? INetHandler else null
        if (activeHandler is net.minecraft.client.network.NetHandlerPlayClient && activePlayer != null) {
            val gameType = activeController?.currentGameType
                ?: net.minecraft.world.WorldSettings.GameType.SURVIVAL
            val newController = net.minecraft.client.multiplayer.PlayerControllerMP(mc, activeHandler)
            newController.setGameType(gameType)
            mc.playerController = newController
            runtime.playerController = newController
            logRuntimeInfo(runtime, "Created fresh PlayerControllerMP with correct handler " +
                "(gameType=$gameType)")
        }

        // Update the NetHandlerPlayClient's clientWorldController to match the
        // runtime's world. This is critical because many packet handlers read
        // clientWorldController directly instead of through mc.theWorld.
        // Try multiple field names (SRG, MCP, and search by type) for robustness.
        val handler = activeHandler ?: runtime.currentHandler
        if (handler is net.minecraft.client.network.NetHandlerPlayClient) {
            val worldFieldSet = runCatching {
                var clazz: Class<*>? = handler.javaClass
                while (clazz != null) {
                    for (fieldName in arrayOf("field_147300_g", "clientWorldController")) {
                        try {
                            val worldField = clazz.getDeclaredField(fieldName)
                            worldField.isAccessible = true
                            worldField.set(handler, runtime.world)
                            logRuntimeInfo(runtime, "Set clientWorldController via field $fieldName")
                            return@runCatching true
                        } catch (_: NoSuchFieldException) { /* try next */ }
                    }
                    // Try finding by type (WorldClient field)
                    try {
                        for (field in clazz.declaredFields) {
                            if (field.type == net.minecraft.client.multiplayer.WorldClient::class.java) {
                                field.isAccessible = true
                                field.set(handler, runtime.world)
                                logRuntimeInfo(runtime, "Set clientWorldController via type search: ${field.name}")
                                return@runCatching true
                            }
                        }
                    } catch (_: Exception) { /* try superclass */ }
                    clazz = clazz.superclass
                }
                false
            }
            if (worldFieldSet.isFailure) {
                logRuntimeWarn(runtime, "Failed to set clientWorldController on handler!")
            }
            // Also verify the handler's networkManager is registered in nmToTabId
            val handlerNm = handler.networkManager
            if (handlerNm != null && nmToTabId.getOrDefault(handlerNm, null) == null) {
                logRuntimeWarn(runtime, "Handler's NM not in nmToTabId — re-registering")
                nmToTabId[handlerNm] = tabId
            }
        }

        runtime.currentHandler = activeHandler ?: runtime.currentHandler
        runtime.backgroundNetworkManager = activePlayer?.sendQueue?.networkManager ?: runtime.backgroundNetworkManager
        SessionRuntimeScope.resetSharedActionStateForCurrentPlayer()
        net.asd.union.utils.movement.MovementUtils.resetForTabSwitch()
        net.asd.union.utils.movement.MovementUtils.resetTimerSpeed()
        InventoryUtils.resetForTabSwitch()
        val player = runtime.player
        if (player != null) {
            runCatching {
                player::class.java.getMethod("asdUnion\$resetPositionTracking").invoke(player)
            }
        }
        runtime.refreshDebugName()
        logRuntimeInfo(runtime, "Activated runtime (thread stopped, main thread takes over)")

        // Diagnostic: log the full state after activation to help debug
        // block placement and action desync issues
        logRuntimeInfo(runtime, "Post-activation state: " +
            "player=${mc.thePlayer != null}, " +
            "world=${mc.theWorld != null}, " +
            "controller=${mc.playerController != null}, " +
            "controllerGameType=${mc.playerController?.currentGameType}, " +
            "renderViewEntity=${mc.renderViewEntity != null}, " +
            "sendQueue=${mc.thePlayer?.sendQueue != null}, " +
            "nmOpen=${mc.thePlayer?.sendQueue?.networkManager?.isChannelOpen}")

        // Clear the cached NetworkPlayerInfo on all player entities in the
        // world. This forces a fresh lookup from the handler's playerInfoMap
        // when the entity is next rendered. Without this, entities created on
        // the sim thread may have cached a dummy NetworkPlayerInfo (from
        // safeGetPlayerInfoForSpawn) that doesn't have the real skin textures.
        // The cache in AbstractClientPlayer.playerInfo is never invalidated
        // by vanilla, so we must clear it manually on tab switch.
        runCatching {
            val world = runtime.world
            if (world != null) {
                // Find the playerInfo field by trying multiple names and type search
                var playerInfoField: java.lang.reflect.Field? = null
                var clazz: Class<*>? = net.minecraft.client.entity.AbstractClientPlayer::class.java
                while (clazz != null && playerInfoField == null) {
                    for (fieldName in arrayOf("field_175157_a", "playerInfo")) {
                        try {
                            val f = clazz.getDeclaredField(fieldName)
                            if (f.type == net.minecraft.client.network.NetworkPlayerInfo::class.java) {
                                playerInfoField = f
                                break
                            }
                        } catch (_: Exception) { /* try next */ }
                    }
                    if (playerInfoField == null) {
                        // Try finding by type
                        for (f in clazz.declaredFields) {
                            if (f.type == net.minecraft.client.network.NetworkPlayerInfo::class.java) {
                                playerInfoField = f
                                break
                            }
                        }
                    }
                    clazz = clazz.superclass
                }
                if (playerInfoField != null) {
                    playerInfoField.isAccessible = true
                    for (entity in world.loadedEntityList) {
                        if (entity is net.minecraft.client.entity.AbstractClientPlayer) {
                            try { playerInfoField.set(entity, null) } catch (_: Exception) { /* skip */ }
                        }
                    }
                }
            }
        }

        // Rebuild the renderer for the new tab's world. Use a lightweight
        // approach to avoid the massive lag spike that occurs when switching
        // to a tab that just joined a server.
        runCatching {
            ChunkOptimizer.skipNextLoadRenderers = false
            mc.renderGlobal.setWorldAndLoadRenderers(runtime.world)
        }

        // Only compile the player's immediate chunk column synchronously
        // for basic rendering. All other chunks are compiled asynchronously
        // by the render loop on demand. This avoids the massive lag spike
        // from forceCompileAllVisibleChunks() which compiles ALL chunks
        // synchronously.
        runCatching { compilePlayerChunkColumn() }

        sanitizeScreen(runtime.currentScreen)?.let { screen ->
            runtime.currentScreen = screen
            val scaledResolution = net.minecraft.client.gui.ScaledResolution(mc)
            screen.setWorldAndResolution(mc, scaledResolution.scaledWidth, scaledResolution.getScaledHeight())
            mc.currentScreen = screen
        } ?: run {
            runtime.currentScreen = null
            mc.displayGuiScreen(null)
        }

        // Now that the mc state is fully restored, process any pending packets
        // from the NM. This ensures that packets like entity spawns, equipment
        // updates, etc. are processed before the next render frame.
        runCatching {
            runtime.networkManager?.takeIf { it.isChannelOpen }?.processReceivedPackets()
        }

        return true
    }

    // ── Simulation thread management ───────────────────────────────────────

    private fun startSimulationThread(runtime: LiveTabRuntime) {
        val existing = runtime.simulationThread
        if (existing != null && existing.isAlive) {
            existing.paused = false
            logRuntimeInfo(runtime, "Resuming existing simulation thread")
            return
        }

        // If there's a dead thread reference, clear it
        if (existing != null && !existing.isAlive) {
            logRuntimeWarn(runtime, "Previous simulation thread is dead, creating new one")
            runtime.simulationThread = null
        }

        val thread = TabSimulationThread(runtime)
        thread.isDaemon = true
        // Add an UncaughtExceptionHandler to catch any exceptions that
        // prevent the thread from producing output
        thread.setUncaughtExceptionHandler { t, e ->
            System.err.println("[TabSim][${runtime.debugLabel()}] UNCAUGHT EXCEPTION in thread ${t.name}: ${e.message}")
            e.printStackTrace(System.err)
            ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Uncaught exception in simulation thread", e)
        }
        runtime.simulationThread = thread
        thread.start()
        logRuntimeInfo(runtime, "Started new simulation thread")
    }

    private fun stopSimulationThread(runtime: LiveTabRuntime) {
        val thread = runtime.simulationThread ?: return
        thread.paused = true
        // Keep the reference so we can resume the thread later instead of
        // creating a new one. Setting simulationThread = null caused a thread
        // leak where paused threads were never cleaned up or resumed.
        logRuntimeInfo(runtime, "Paused simulation thread (keeping reference for resume)")
    }

    // ── Packet routing ─────────────────────────────────────────────────────

    /**
     * Called from MixinNetworkManager when a packet arrives on the Netty thread.
     * If the packet belongs to a background tab, enqueue it to that tab's
     * simulation thread instead of letting it go through the main thread.
     */
    fun enqueueIncomingPacket(handler: INetHandler?, packet: Packet<*>, sourceNetworkManager: NetworkManager? = null): Boolean {
        // Try to find the runtime by handler first (fast path)
        var runtime = findBackgroundRuntime(handler)

        // Fallback: match by the source NetworkManager directly. This is more robust
        // than matching by handler.networkManager because the handler might not have
        // its NetworkManager set yet, or the runtime's backgroundNetworkManager might
        // not match handler.networkManager.
        if (runtime == null && sourceNetworkManager != null) {
            val found = findRuntimeByNetworkManager(sourceNetworkManager)
            if (found != null && found.tabId != activeRuntimeTabId) {
                runtime = found
            }
        }

        // Fallback: match by the handler's NetworkManager. This is critical for
        // new tabs where currentHandler hasn't been set yet (e.g., during
        // handleJoinGame the NetHandlerPlayClient was just created by
        // NetHandlerLoginClient and the runtime doesn't know about it yet).
        if (runtime == null && handler is NetHandlerPlayClient) {
            val nm = handler.networkManager
            if (nm != null) {
                val found = findRuntimeByNetworkManager(nm)
                if (found != null && found.tabId != activeRuntimeTabId) {
                    runtime = found
                }
            }
        }

        if (runtime == null) {
            // The packet belongs to the active tab or no known tab — this is
            // expected for the active tab (packets go through vanilla flow).
            // Only log if the NM mapping suggests this should be a background tab
            // but we couldn't find a runtime (actual routing failure).
            val sourceTabId = sourceNetworkManager?.let { nmToTabId[it] }
            val handlerTabId = if (handler is NetHandlerPlayClient) handler.networkManager?.let { nmToTabId[it] } else null
            val isBackgroundTab = (sourceTabId != null && sourceTabId != activeRuntimeTabId)
                || (handlerTabId != null && handlerTabId != activeRuntimeTabId)

            if (isBackgroundTab) {
                // This is a real routing failure — the NM mapping says this packet
                // should go to a background tab, but we couldn't find a runtime.
                ClientUtils.LOGGER.warn("[TabSim] enqueueIncomingPacket ROUTING FAILURE for {}: " +
                    "handler={}, handlerId={}, sourceNmId={}, sourceTabId={}, handlerTabId={}, activeTab={}, runtimes={}, nmMapSize={}",
                    packet.javaClass.simpleName,
                    handler?.javaClass?.simpleName,
                    handler?.let { System.identityHashCode(it) },
                    sourceNetworkManager?.let { System.identityHashCode(it) },
                    sourceTabId,
                    handlerTabId,
                    activeRuntimeTabId,
                    runtimes.keys,
                    nmToTabId.size)
            }
            return false
        }

        // If the runtime is disconnected, drop the packet silently.
        // This happens when a background tab was kicked (e.g., "already connected
        // to this proxy") but packets are still in the Netty pipeline.
        if (!runtime.connected && runtime.disconnectedReason != null) {
            return true // Packet "handled" (dropped)
        }

        if (shouldDropDetachedIncomingPacket(packet)) {
            return true
        }

        @Suppress("UNCHECKED_CAST")
        val rawPacket = packet as Packet<INetHandler>

        // Update the runtime's handler reference if it was missing
        if (runtime.currentHandler !== handler && handler != null) {
            runtime.currentHandler = handler
        }

        // If the simulation thread is running, enqueue to its packet queue
        val simThread = runtime.simulationThread
        if (simThread != null && simThread.isAlive && !simThread.paused) {
            simThread.enqueuePacket(rawPacket)
            // Diagnostic: log entity spawn packets being routed to background tabs
            // DEBUG: if (packet is net.minecraft.network.play.server.S0CPacketSpawnPlayer
            // DEBUG:     || packet is net.minecraft.network.play.server.S0EPacketSpawnObject
            // DEBUG:     || packet is net.minecraft.network.play.server.S0FPacketSpawnMob
            // DEBUG: ) {
            // DEBUG:     ClientUtils.LOGGER.info("[TabSim] Routed {} to background tab {} (entities={})",
            // DEBUG:         packet.javaClass.simpleName, runtime.debugLabel(),
            // DEBUG:         runtime.world?.loadedEntityList?.size ?: 0)
            // DEBUG: }
            return true
        }

        // Fallback: enqueue as a task (for tabs without a running thread yet)
        runtime.enqueueTask(Runnable {
            rawPacket.processPacket(handler)
        })
        return true
    }

    /**
     * Called from MixinPacketThreadUtil when a packet needs to be scheduled
     * to a thread. If the handler belongs to a background tab, schedule to
     * that tab's simulation thread.
     *
     * Uses the NM→tabId mapping as the primary routing mechanism, which is
     * more reliable than matching by handler reference.
     */
    fun enqueueScheduledTask(handler: INetHandler?, task: Runnable): Boolean {
        var runtime = findBackgroundRuntime(handler)

        // Fallback: use NM mapping (most reliable)
        if (runtime == null && handler is NetHandlerPlayClient) {
            val nm = handler.networkManager
            if (nm != null) {
                val found = findRuntimeByNetworkManager(nm)
                if (found != null && found.tabId != activeRuntimeTabId) {
                    runtime = found
                }
            }
        }

        if (runtime == null) return false

        // Update the runtime's handler reference if it was missing
        if (runtime.currentHandler !== handler && handler != null) {
            runtime.currentHandler = handler
        }

        val simThread = runtime.simulationThread
        if (simThread != null && simThread.isAlive && !simThread.paused) {
            runtime.enqueueTask(task)
            return true
        }

        runtime.enqueueTask(task)
        return true
    }

    /**
     * Checks if a handler belongs to an orphaned background tab connection.
     * This happens when a background tab was disconnected and its runtime was
     * cleaned up, but the NetworkManager hasn't been fully closed yet and
     * packets are still arriving on the Netty pipeline.
     *
     * Used by MixinNetworkManager to drop these packets instead of processing
     * them on the main thread (which would cause NPEs).
     */
    fun isOrphanedBackgroundHandler(handler: INetHandler?): Boolean {
        if (handler == null) return false

        // Check if this handler belongs to any runtime (active or background)
        for (runtime in runtimes.values) {
            if (runtime.currentHandler === handler) {
                return false // Has a runtime — not orphaned
            }
        }

        // Check by NetworkManager
        if (handler is NetHandlerPlayClient) {
            val nm = handler.networkManager
            if (nm != null) {
                for (runtime in runtimes.values) {
                    if (runtime.backgroundNetworkManager === nm || runtime.networkManager === nm) {
                        // Found by NetworkManager — check if disconnected
                        return !runtime.connected
                    }
                }
            }

            // IMPORTANT: Check if this is the main thread's current handler.
            // The main Minecraft connection handler is NOT stored in any runtime,
            // so it would otherwise be incorrectly classified as orphaned.
            val mainHandler = mc.thePlayer?.sendQueue as? INetHandler
            if (mainHandler === handler) {
                return false // Main thread's handler — never orphaned
            }
        }

        // If the handler doesn't belong to any runtime, is not the main thread's
        // handler, and is a NetHandlerPlayClient, it's likely from a disconnected
        // background tab whose runtime was cleaned up.
        return handler is NetHandlerPlayClient
    }

    /**
     * Check if the current thread is a simulation thread for a background tab.
     * Used by MixinPacketThreadUtil to allow packets to be processed directly
     * on simulation threads without scheduling to the main thread.
     */
    fun isSimulationThread(): Boolean {
        return Thread.currentThread() is TabSimulationThread
    }

    // ── Connection management ──────────────────────────────────────────────

    fun clearMinecraftRuntime(screen: GuiScreen? = null) {
        System.out.println("[TabDebug][clearMinecraftRuntime] ENTER. screen=${screen?.javaClass?.simpleName}")
        System.out.println("[TabDebug][clearMinecraftRuntime] mc BEFORE: theWorld=${mc.theWorld != null}, " +
            "thePlayer=${mc.thePlayer != null}, controller=${mc.playerController != null}, " +
            "currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
        System.out.flush()

        activeRuntimeTabId = null

        val targetScreen = when {
            screen == null -> GuiMainMenu()
            screen is net.minecraft.client.gui.inventory.GuiContainer -> GuiMainMenu()
            else -> screen
        }
        System.out.println("[TabDebug][clearMinecraftRuntime] STEP 1: displayGuiScreen($targetScreen)")
        System.out.flush()
        mc.displayGuiScreen(targetScreen)
        System.out.println("[TabDebug][clearMinecraftRuntime] STEP 1 done. currentScreen=${mc.currentScreen?.javaClass?.simpleName}")
        System.out.flush()

        System.out.println("[TabDebug][clearMinecraftRuntime] STEP 2: Nulling mc state")
        System.out.flush()
        mc.theWorld = null
        mc.thePlayer = null
        mc.playerController = null
        mc.renderViewEntity = null
        mc.objectMouseOver = null
        mc.pointedEntity = null
        mc.setServerData(null)
        System.out.println("[TabDebug][clearMinecraftRuntime] STEP 2 done. " +
            "theWorld=${mc.theWorld != null}, thePlayer=${mc.thePlayer != null}, " +
            "controller=${mc.playerController != null}")
        System.out.flush()

        runCatching {
            mc.renderGlobal.setWorldAndLoadRenderers(null)
        }
        System.out.println("[TabDebug][clearMinecraftRuntime] DONE")
        System.out.flush()
    }

    fun removeRuntime(tabId: String, disconnect: Boolean = true) {
        val runtime = runtimes.remove(tabId) ?: return
        deactivateRuntime(tabId)
        // Clean up NM mapping (idempotent)
        if (runtime.backgroundNetworkManager != null && nmToTabId[runtime.backgroundNetworkManager] == tabId) {
            nmToTabId.remove(runtime.backgroundNetworkManager)
        }
        if (runtime.networkManager != null && nmToTabId[runtime.networkManager] == tabId) {
            nmToTabId.remove(runtime.networkManager)
        }
        // Properly shut down the simulation thread (not just pause)
        runtime.simulationThread?.let { thread ->
            thread.shutdown()
            runtime.simulationThread = null
        }
        runtime.clearQueuedTasks()
        logRuntimeInfo(runtime, "Removing runtime (disconnect=$disconnect)")
        if (disconnect) {
            runtime.disconnect("Tab closed")
        }
    }

    fun mirrorCurrentScreen(tabId: String, screen: GuiScreen?, serverData: ServerData?) {
        val runtime = runtimes.getOrPut(tabId) { LiveTabRuntime(tabId) }
        runtime.currentScreen = sanitizeScreen(screen)
        runtime.serverData = serverData
        runtime.session = ClientTabManager.sessionForTab(tabId) ?: runtime.session
    }

    fun connectRuntime(tabId: String, session: Session, serverData: ServerData, screen: GuiScreen? = null): Boolean {
        val runtime = runtimes.getOrPut(tabId) { LiveTabRuntime(tabId) }
        runtime.session = session
        runtime.debugName = session.username
        runtime.serverData = serverData
        runtime.currentScreen = sanitizeScreen(screen)
        runtime.disconnectedReason = null
        logRuntimeInfo(runtime, "Connecting to ${serverData.serverIP}")

        // Refresh the router tunnel before connecting to ensure the fastest
        // direct path is used (bypasses VPN when tunnel is available).
        runCatching {
            net.asd.union.handler.network.ConnectToRouter.ultraFastRefreshServerPing()
        }

        // IMPORTANT: Do NOT use SessionRuntimeScope.runDetached here.
        // runDetached temporarily sets mc.theWorld = null, mc.thePlayer = null,
        // which corrupts the active tab's state. The async login response arrives
        // AFTER runDetached restores the state, causing the login→play transition
        // to call mc.loadWorld(null) on the main thread, disconnecting the active player.
        //
        // Instead, we create the NetworkManager directly without touching the mc
        // singleton. The simulation thread handles all packet processing, and the
        // MixinNetHandlerLoginClient redirects mc.getSession() to return the
        // correct session for this tab's NetworkManager.

        return runCatching {
            val serverAddress = net.minecraft.client.multiplayer.ServerAddress.fromString(serverData.serverIP)

            // When the tunnel is active, skip DNS resolution and use loopback.
            // The tunnel at 127.0.0.1:25560 handles routing to the real server,
            // and DNS may fail for hostnames only resolvable through the tunnel.
            val shouldUseTunnel = net.asd.union.handler.network.ConnectToRouter.enabled
                    && (net.asd.union.handler.network.ConnectToRouter.isTunnelMode()
                        || net.asd.union.handler.network.ConnectToRouter.tunnelAvailable)
            val inetAddress = if (shouldUseTunnel) {
                java.net.InetAddress.getByName("127.0.0.1")
            } else {
                java.net.InetAddress.getByName(serverAddress.ip)
            }
            val networkManager = NetworkManager.createNetworkManagerAndConnect(
                inetAddress,
                serverAddress.port,
                mc.gameSettings.isUsingNativeTransport
            )

            // Set the NetworkManager in the runtime IMMEDIATELY after creation,
            // before any packets are sent. The server's encryption response can
            // arrive on the Netty thread at any time after the connection is
            // established, and MixinNetHandlerLoginClient needs to find the
            // runtime by NetworkManager to return the correct session.
            runtime.backgroundNetworkManager = networkManager
            if (nmToTabId.getOrDefault(networkManager, null) == null) {
                nmToTabId[networkManager] = tabId
            }

            // Create the login handler. The NetHandlerLoginClient stores a reference
            // to mc, but MixinNetHandlerLoginClient intercepts mc.getSession() to
            // return the correct session for this tab's NM.
            networkManager.netHandler = net.minecraft.client.network.NetHandlerLoginClient(
                networkManager,
                mc,
                screen ?: GuiMainMenu()
            )

            // Store the login handler as the current handler so the simulation
            // thread can process login packets via drainPackets().
            runtime.currentHandler = networkManager.netHandler

            networkManager.sendPacket(
                net.minecraft.network.handshake.client.C00Handshake(
                    47,
                    serverAddress.ip,
                    serverAddress.port,
                    net.minecraft.network.EnumConnectionState.LOGIN,
                    true
                )
            )
            // Use the tab's own session directly for the login packet.
            // This is critical because the login handshake is async — by the
            // time the server responds with an authentication challenge,
            // mc.session still points to the active tab's session.
            // Using the runtime's session ensures the correct authentication
            // token is used regardless of mc.session state.
            networkManager.sendPacket(
                net.minecraft.network.login.client.C00PacketLoginStart(session.profile)
            )

            // Start the simulation thread IMMEDIATELY so it can process
            // login packets (S00PacketLoginSuccess, encryption requests, etc.)
            // Without this, login packets are enqueued as tasks but never processed
            // because no simulation thread exists yet.
            startSimulationThread(runtime)

            true
        }.getOrElse {
            runtime.disconnectedReason = it.message ?: "Failed to connect"
            logRuntimeWarn(runtime, "Connect failed: ${runtime.disconnectedReason}")
            false
        }
    }

    /**
     * Registers a newly joined world/player in a runtime so they persist after
     * the detached context restores the active tab's state.
     */
    fun registerDetachedJoin(
        handler: NetHandlerPlayClient,
        session: Session,
        serverData: ServerData?,
        world: WorldClient,
        player: EntityPlayerSP,
        playerController: PlayerControllerMP
    ) {
        val networkManager = handler.networkManager

        // Find the existing runtime that owns this network manager
        val runtime = runtimes.values.firstOrNull { runtime ->
            runtime.networkManager === networkManager || runtime.backgroundNetworkManager === networkManager
        } ?: run {
            val fallbackId = "detached-${System.identityHashCode(networkManager)}"
            logRuntimeWarn(LiveTabRuntime(fallbackId), "No matching runtime for detached join, creating fallback")
            runtimes.getOrPut(fallbackId) { LiveTabRuntime(fallbackId) }
        }

        runtime.session = session
        runtime.debugName = session.username
        runtime.serverData = serverData
        runtime.world = world
        runtime.player = player
        runtime.playerController = playerController
        runtime.backgroundNetworkManager = networkManager
        runtime.currentHandler = handler
        runtime.disconnectedReason = null
        runtime.connected = true
        runtime.lastJoinTime = System.currentTimeMillis()

        // Register the NM mapping for reliable packet routing
        if (nmToTabId.getOrDefault(networkManager, null) == null) {
            nmToTabId[networkManager] = runtime.tabId
        }

        logRuntimeInfo(runtime, "Detached join registered: world=${world.provider.dimensionId}, player=${player.name}")

        // Notify the multi-select queue that this tab has successfully connected
        MultiSelectJoinQueue.onTabConnected(runtime.tabId)

        // Start the simulation thread for this background tab
        startSimulationThread(runtime)
    }

    fun disconnectRuntimeToScreen(tabId: String, reason: String, screen: GuiScreen?) {
        val runtime = runtimes[tabId] ?: return
        stopSimulationThread(runtime)
        logRuntimeWarn(runtime, "Disconnecting to screen: $reason")
        // Clean up NM mapping (idempotent)
        if (runtime.backgroundNetworkManager != null && nmToTabId[runtime.backgroundNetworkManager] == tabId) {
            nmToTabId.remove(runtime.backgroundNetworkManager)
        }
        if (runtime.networkManager != null && nmToTabId[runtime.networkManager] == tabId) {
            nmToTabId.remove(runtime.networkManager)
        }
        runtime.disconnect(reason)

        // Store the screen before clearing state
        runtime.currentScreen = screen ?: GuiDisconnected(
            GuiMultiplayer(GuiMainMenu()),
            "disconnect.lost",
            ChatComponentText(reason)
        )

        // Only null the runtime's world/player state if this is NOT the active tab.
        // If it IS the active tab, the screen is already displayed and the game
        // loop will handle the transition. Nulling the active tab's state here
        // causes NPE in runTick.
        if (tabId != activeRuntimeTabId) {
            runtime.world = null
            runtime.player = null
            runtime.playerController = null
            runtime.renderViewEntity = null
        }
        // Keep backgroundNetworkManager for packet routing
        runtime.connected = false
    }

    /**
     * No longer needed — background tabs are ticked by their own threads.
     * Kept as a no-op for compatibility with MixinMinecraft.
     */
    private var tickBackgroundCounter = 0L

    fun tickBackgroundRuntimes(activeTabId: String?) {
        // Watchdog: detect and restart dead simulation threads for background tabs.
        // This ensures background tabs keep running even if a thread crashes.
        for ((tabId, runtime) in runtimes) {
            if (tabId == activeTabId) continue
            if (!runtime.hasWorldState) continue

            val thread = runtime.simulationThread
            if (thread == null) {
                // No thread exists but tab has world state — start one
                logRuntimeWarn(runtime, "Watchdog: no simulation thread but has world state, starting one")
                startSimulationThread(runtime)
            } else if (!thread.isAlive) {
                // Thread is dead — restart it
                logRuntimeWarn(runtime, "Watchdog: simulation thread is dead, restarting")
                runtime.simulationThread = null
                startSimulationThread(runtime)
            }
            // If thread is alive but paused, that's expected (tab was just activated
            // and will be deactivated soon, or the thread is about to resume)
        }

        // Periodic diagnostic: log NM mapping and entity counts every ~10 seconds
        tickBackgroundCounter++
        if (tickBackgroundCounter % 400 == 0L && runtimes.isNotEmpty()) {
            val activeTab = activeTabId ?: "null"
            val nmMapEntries = nmToTabId.entries.joinToString(", ") { (nm, id) ->
                "${System.identityHashCode(nm)}->$id"
            }
            val runtimeStates = runtimes.entries.joinToString("; ") { (id, rt) ->
                val entityCount = rt.world?.loadedEntityList?.size ?: 0
                val playerCount = rt.world?.playerEntities?.size ?: 0
                val nmId = rt.backgroundNetworkManager?.let { System.identityHashCode(it) } ?: "null"
                "$id:nm=$nmId,entities=$entityCount,players=$playerCount,connected=${rt.connected},thread=${rt.simulationThread?.isAlive}"
            }
            // DEBUG: ClientUtils.LOGGER.info("[TabSim] Diagnostic: activeTab=$activeTab, nmMap=[$nmMapEntries], runtimes=[$runtimeStates]")
        }
    }

    fun markCurrentRuntimeDisconnected(reason: IChatComponent?) {
        SessionRuntimeScope.currentRuntime()?.let { runtime ->
            moveRuntimeToDisconnectedScreen(runtime, reason)
            logRuntimeWarn(runtime, "Server disconnected runtime: ${reason?.unformattedText ?: "Disconnected"}")
        }
    }

    fun findRuntimeForHandler(handler: INetHandler?): LiveTabRuntime? {
        // First try background runtimes (most common case)
        val background = findBackgroundRuntime(handler)
        if (background != null) return background
        // Also check active runtime (for cases where handler is on the active tab)
        val byHandler = runtimes.values.firstOrNull { it.currentHandler === handler }
        if (byHandler != null) return byHandler
        // Fallback: match by NetworkManager (for new tabs where currentHandler
        // hasn't been set yet, e.g., during handleJoinGame)
        if (handler is NetHandlerPlayClient) {
            val nm = handler.networkManager
            if (nm != null) {
                return findRuntimeByNetworkManager(nm)
            }
        }
        return null
    }

    fun scheduleRuntimeDisconnect(runtime: LiveTabRuntime, reason: IChatComponent?) {
        runtime.disconnectedReason = reason?.unformattedText ?: "Disconnected"
        runtime.enqueueTask(Runnable {
            moveRuntimeToDisconnectedScreen(runtime, reason)
            logRuntimeWarn(runtime, "Server disconnected runtime: ${reason?.unformattedText ?: "Disconnected"}")
        })
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun moveRuntimeToDisconnectedScreen(runtime: LiveTabRuntime, reason: IChatComponent?) {
        stopSimulationThread(runtime)
        runtime.clearQueuedTasks()

        // Set the disconnect screen BEFORE clearing state so the game loop
        // has a valid screen to render during the transition
        runtime.currentScreen = GuiDisconnected(
            GuiMultiplayer(GuiMainMenu()),
            "disconnect.lost",
            reason?.createCopy() ?: ChatComponentText("Disconnected")
        )

        // Only null world/player state if this is NOT the active tab.
        // If it IS the active tab, the mc singleton still holds references
        // to these objects and nulling them here causes NPE in runTick.
        // The active tab's state will be cleaned up when the user switches
        // away or when clearMinecraftRuntime is called.
        if (runtime.tabId != activeRuntimeTabId) {
            runtime.world = null
            runtime.player = null
            runtime.playerController = null
            runtime.renderViewEntity = null
        }
        // Keep backgroundNetworkManager for packet routing — orphaned packets
        // from the closing connection need to be identified and dropped.
        // It will be cleared when the runtime is fully removed.
        runtime.connected = false
        runtime.disconnectedReason = null

        // Notify the multi-select queue that this tab has disconnected
        MultiSelectJoinQueue.onTabDisconnected(runtime.tabId)
    }

    private fun findBackgroundRuntime(handler: INetHandler?): LiveTabRuntime? {
        return runtimes.values.firstOrNull { runtime ->
            runtime.tabId != activeRuntimeTabId && runtime.currentHandler === handler
        }
    }

    /**
     * Checks if the given handler belongs to any background tab runtime.
     * Uses the NM→tabId mapping as the primary check for reliability.
     */
    fun isHandlerForBackgroundTab(handler: INetHandler): Boolean {
        // Fast path: check by handler's NetworkManager using the NM mapping
        if (handler is NetHandlerPlayClient) {
            val handlerNm = handler.networkManager
            if (handlerNm != null) {
                val tabId = nmToTabId[handlerNm]
                if (tabId != null && tabId != activeRuntimeTabId) {
                    return true
                }
            }
        }
        // Fallback: check by handler reference
        for (runtime in runtimes.values) {
            if (runtime.tabId == activeRuntimeTabId) continue
            if (runtime.currentHandler === handler) return true
        }
        return false
    }

    /**
     * Checks if a handler belongs to a tab whose world is not yet ready.
     * This handles the critical case where:
     *   - A tab is the ACTIVE tab (so isHandlerForBackgroundTab returns false)
     *   - But its NetHandlerPlayClient was just created by handleLoginSuccess
     *   - clientWorldController is null because handleJoinGame hasn't run yet
     *   - PLAY-state packets would NPE if processed on the main thread
     *
     * In this case, packets should be queued or dropped rather than processed.
     */
    fun isHandlerWorldNotReady(handler: INetHandler): Boolean {
        if (handler is NetHandlerPlayClient) {
            val nm = handler.networkManager ?: return false
            val tabId = nmToTabId[nm] ?: return false
            val runtime = runtimes[tabId] ?: return false
            // If the runtime has no world, the handler's clientWorldController
            // is null and packets will NPE. Drop them.
            return runtime.world == null
        }
        return false
    }

    /**
     * Force-routes a packet to a background tab's simulation thread using
     * the NetworkManager as the primary lookup key. This is called when
     * enqueueIncomingPacket fails to route the packet (e.g., because the
     * handler reference doesn't match any runtime's currentHandler).
     *
     * Uses the NM→tabId mapping for O(1) lookup.
     */
    fun forceRouteToBackgroundTab(handler: INetHandler?, packet: Packet<*>, sourceNetworkManager: NetworkManager): Boolean {
        // Find the runtime using the NM mapping
        val runtime = findRuntimeByNetworkManager(sourceNetworkManager) ?: return false
        if (runtime.tabId == activeRuntimeTabId) return false // Active tab — not a background tab

        // Found a background tab runtime — route the packet to it
        if (!runtime.connected && runtime.disconnectedReason != null) {
            return true // Drop the packet (disconnected tab)
        }

        if (shouldDropDetachedIncomingPacket(packet)) {
            return true
        }

        @Suppress("UNCHECKED_CAST")
        val rawPacket = packet as Packet<INetHandler>

        // Update the runtime's handler reference if it was missing or stale
        if (runtime.currentHandler !== handler && handler != null) {
            runtime.currentHandler = handler
        }

        ClientUtils.LOGGER.warn("[TabSim] Force-routed {} to background tab {} (handler match failed, used NetworkManager)",
            packet.javaClass.simpleName, runtime.debugLabel())

        // If the simulation thread is running, enqueue to its packet queue
        val simThread = runtime.simulationThread
        if (simThread != null && simThread.isAlive && !simThread.paused) {
            simThread.enqueuePacket(rawPacket)
            return true
        }

        // Fallback: enqueue as a task (for tabs without a running thread yet)
        runtime.enqueueTask(Runnable {
            rawPacket.processPacket(handler)
        })
        return true
    }

    /**
     * Compiles only the player's immediate chunk column synchronously.
     * This provides basic rendering immediately while the render loop
     * compiles other chunks asynchronously on demand.
     *
     * This is much faster than forceCompileAllVisibleChunks() which
     * compiles ALL visible chunks synchronously, causing massive lag
     * when switching to a tab that just joined a server.
     */
    private fun compilePlayerChunkColumn() {
        val renderGlobal = mc.renderGlobal ?: return
        val viewEntity = mc.renderViewEntity ?: return
        val world = mc.theWorld ?: return
        val viewFrustum = renderGlobal.viewFrustum ?: return
        val renderDispatcher = renderGlobal.renderDispatcher ?: return

        val playerChunkX = net.minecraft.util.MathHelper.floor_double(viewEntity.posX) shr 4
        val playerChunkZ = net.minecraft.util.MathHelper.floor_double(viewEntity.posZ) shr 4

        // Mark all visible chunks as needing update so the render loop
        // compiles them asynchronously
        for (x in -mc.gameSettings.renderDistanceChunks..mc.gameSettings.renderDistanceChunks) {
            for (z in -mc.gameSettings.renderDistanceChunks..mc.gameSettings.renderDistanceChunks) {
                val cx = playerChunkX + x
                val cz = playerChunkZ + z
                if (!world.chunkProvider.chunkExists(cx, cz)) continue

                for (y in 0..15) {
                    val blockPos = net.minecraft.util.BlockPos((cx shl 4) + 8, y shl 4, (cz shl 4) + 8)
                    val renderChunk = runCatching { viewFrustum.getRenderChunk(blockPos) }.getOrNull() ?: continue
                    renderChunk.setNeedsUpdate(true)
                }
            }
        }

        // Synchronously compile ONLY the player's own chunk column for
        // immediate block rendering. All other chunks will be compiled
        // asynchronously by the render loop.
        for (y in 0..15) {
            val blockPos = net.minecraft.util.BlockPos(viewEntity.posX.toInt(), y shl 4, viewEntity.posZ.toInt())
            val renderChunk = runCatching { viewFrustum.getRenderChunk(blockPos) }.getOrNull() ?: continue
            renderDispatcher.updateChunkNow(renderChunk)
        }
    }

    /**
     * Find a runtime by its NetworkManager instance. Uses the nmToTabId
     * mapping as the primary lookup (O(1)), with a linear scan fallback.
     */
    private fun findRuntimeByNetworkManager(nm: NetworkManager): LiveTabRuntime? {
        // Fast path: use the direct NM→tabId mapping
        val tabId = nmToTabId[nm]
        if (tabId != null) {
            return runtimes[tabId]
        }
        // Slow fallback: linear scan (for NMs registered before the mapping was added)
        return runtimes.values.firstOrNull { runtime ->
            runtime.backgroundNetworkManager === nm || runtime.networkManager === nm
        }
    }

    /**
     * Public version of findRuntimeByNetworkManager for use by mixins.
     * Used by MixinNetHandlerLoginClient to find the correct session for
     * a connecting tab's NetworkManager.
     */
    fun findRuntimeByNetworkManagerPublic(nm: NetworkManager): LiveTabRuntime? {
        return findRuntimeByNetworkManager(nm)
    }

    /**
     * Gets the NetworkManager from a NetHandlerPlayClient using reflection.
     * This is needed because the field name is SRG-obfuscated and not
     * accessible from Java mixins without @Shadow.
     */
    fun getHandlerNetworkManager(handler: INetHandler): NetworkManager? {
        if (handler is NetHandlerPlayClient) {
            return try {
                // Try SRG name first, then MCP name, then type search
                val handlerClass = handler.javaClass
                var field = try {
                    handlerClass.getDeclaredField("field_147302_a")
                } catch (_: NoSuchFieldException) {
                    try {
                        handlerClass.getDeclaredField("netManager")
                    } catch (_: NoSuchFieldException) {
                        null
                    }
                }
                if (field == null) {
                    for (f in handlerClass.declaredFields) {
                        if (f.type == NetworkManager::class.java) {
                            field = f
                            break
                        }
                    }
                }
                if (field != null) {
                    field.isAccessible = true
                    field.get(handler) as? NetworkManager
                } else null
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Checks if a handler belongs to a different tab than the currently active one.
     * Uses direct NetworkManager comparison — the most reliable isolation check.
     * Returns true if the handler belongs to a DIFFERENT tab (should be cancelled/dropped).
     */
    fun isHandlerForDifferentTab(handler: INetHandler): Boolean {
        val mc = net.minecraft.client.Minecraft.getMinecraft()
        val activePlayer = mc.thePlayer ?: return false
        val activeSendQueue = activePlayer.sendQueue ?: return false
        val activeNm = getHandlerNetworkManager(activeSendQueue) ?: return false
        val handlerNm = getHandlerNetworkManager(handler) ?: return false
        return activeNm !== handlerNm
    }

    /**
     * Register a NetworkManager→tabId mapping. Can be called from mixins
     * when a new NM is associated with a tab (e.g., during login→play
     * transition).
     */
    fun registerNetworkManager(nm: NetworkManager, tabId: String) {
        if (nmToTabId.getOrDefault(nm, null) == null) {
            nmToTabId[nm] = tabId
        }
    }

    /**
     * Look up the tab ID for a NetworkManager using the direct mapping.
     * Returns null if the NM is not registered.
     */
    fun findTabIdByNetworkManager(nm: NetworkManager): String? {
        return nmToTabId[nm]
    }

    /**
     * Log a suppressed NPE from packet processing. Called from MixinPacketThreadUtil
     * when a packet processing task throws an NPE (e.g., when clientWorldController
     * is null during initial connection).
     */
    fun logPacketError(packet: Packet<*>, handler: INetHandler, e: RuntimeException) {
        ClientUtils.LOGGER.debug("[TabSim] Suppressed {} in packet {} on handler {}: {}",
            e.javaClass.simpleName, packet.javaClass.simpleName, handler.javaClass.simpleName, e.message)
    }

    private fun shouldDropDetachedIncomingPacket(packet: Packet<*>): Boolean {
        if (!ClientFixes.fmlFixesEnabled) return false

        if (ClientFixes.blockProxyPacket &&
            packet.javaClass.name == "net.minecraftforge.fml.common.network.internal.FMLProxyPacket"
        ) {
            return true
        }

        if (packet is S3FPacketCustomPayload) {
            if (ClientFixes.blockPayloadPackets && !packet.channelName.startsWith("MC|")) {
                return true
            }
            if (packet.channelName == "MC|BOpen") {
                return true
            }
        }

        return false
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// LiveTabRuntime — per-tab state container
// ──────────────────────────────────────────────────────────────────────────────

class LiveTabRuntime(val tabId: String) {
    var session: Session = MinecraftInstance.mc.session
    var debugName: String = session.username
    @Volatile var world: WorldClient? = null
    @Volatile var player: EntityPlayerSP? = null
    @Volatile var playerController: PlayerControllerMP? = null
    var serverData: ServerData? = null
    var renderViewEntity: Entity? = null
    var currentScreen: GuiScreen? = null
    @Volatile var backgroundNetworkManager: NetworkManager? = null
    var disconnectedReason: String? = null
    @Volatile var currentHandler: INetHandler? = null
    @Volatile var connected: Boolean = false
    var lastJoinTime: Long = 0L
    @Volatile var simulationThread: TabSimulationThread? = null

    private val queuedTasks = ConcurrentLinkedQueue<Runnable>()
    private var backgroundTicks = 0L
    private var lastHealthLogAtTick = 0L

    val netHandler: NetHandlerPlayClient?
        get() = player?.sendQueue

    val networkManager: NetworkManager?
        get() = netHandler?.networkManager ?: backgroundNetworkManager

    val hasWorldState: Boolean
        get() = world != null && player != null && playerController != null && player?.worldObj === world

    val hasRestorableState: Boolean
        get() {
            if (disconnectedReason != null) return false
            // Only consider a tab restorable if it has a valid game world.
            // A tab with only a currentScreen (e.g., disconnect screen) but
            // no world/player is NOT restorable for the game state branch —
            // it must go through the "no restorable state" path which properly
            // clears mc state and shows the screen.
            if (hasWorldState) return true
            // A connecting tab (NM open, no world yet) is restorable so the
            // user can see the connecting screen.
            if (networkManager?.isChannelOpen == true && connected) return true
            return false
        }

    fun refreshDebugName() {
        debugName = session.username
    }

    fun normalizeRenderState() {
        renderViewEntity = sanitizeRuntimeRenderViewEntity(world, player, renderViewEntity)
    }

    fun debugLabel(): String = "$debugName/$tabId"

    fun enqueueTask(task: Runnable) {
        queuedTasks += task
    }

    fun drainQueuedTasks(limit: Int = Int.MAX_VALUE): Int {
        if (limit <= 0) return 0

        // If we're on a TabSimulationThread, ensure the ThreadLocal context
        // is set so blanket Mixin redirects return this runtime's state.
        val simThread = Thread.currentThread() as? TabSimulationThread
        val needsContextSet = simThread != null && TabSimulationThread.getCurrentProcessingRuntime() == null

        if (needsContextSet) {
            TabSimulationThread.setCurrentProcessingRuntime(simThread!!.runtime)
        }

        try {
            var processed = 0
            while (processed < limit) {
                val task = queuedTasks.poll() ?: return processed
                runCatching { task.run() }.onFailure { throwable ->
                    ClientUtils.LOGGER.error("Failed to process queued tab task for $tabId", throwable)
                }
                processed++
            }
            return processed
        } finally {
            if (needsContextSet) {
                TabSimulationThread.clearCurrentProcessingRuntime()
            }
        }
    }

    fun clearQueuedTasks() {
        while (queuedTasks.poll() != null) { /* drain */ }
    }

    fun disconnect(reason: String) {
        disconnectedReason = reason
        clearQueuedTasks()

        networkManager?.let { manager ->
            runCatching { manager.closeChannel(ChatComponentText(reason)) }
            runCatching { manager.checkDisconnected() }
        }

        backgroundNetworkManager = null
    }

    fun markDisconnectedScreen(reason: IChatComponent?) {
        ClientUtils.LOGGER.warn("[TabRuntime][${debugLabel()}] markDisconnectedScreen: reason=${reason?.unformattedText}, hasWorld=$hasWorldState, connected=$connected, channelOpen=${networkManager?.isChannelOpen}")
        clearQueuedTasks()
        // Set the disconnect screen first so the game loop has something to render
        currentScreen = GuiDisconnected(
            GuiMultiplayer(GuiMainMenu()),
            "disconnect.lost",
            reason?.createCopy() ?: ChatComponentText("Disconnected")
        )
        // Mark as disconnected but do NOT null world/player/playerController here.
        // The sim thread calls this method, and nulling these fields from the sim
        // thread while the main thread might be accessing them causes race conditions.
        // The state will be cleaned up when the runtime is activated (switched to)
        // or when it's removed.
        connected = false
        disconnectedReason = null
    }

    fun recordBackgroundTick() {
        backgroundTicks++

        if (backgroundTicks - lastHealthLogAtTick < 100L) return

        lastHealthLogAtTick = backgroundTicks
        val player = player
        val world = world
        ClientUtils.LOGGER.info(
            "[TabRuntime][${debugLabel()}] Background tick health: " +
                "world=${world != null}, player=${player != null}, " +
                "openChannel=${networkManager?.isChannelOpen == true}, " +
                "queuedTasks=${queuedTasks.size}, " +
                "pos=${if (player != null) "%.2f, %.2f, %.2f".format(player.posX, player.posY, player.posZ) else "n/a"}"
        )
    }
}
