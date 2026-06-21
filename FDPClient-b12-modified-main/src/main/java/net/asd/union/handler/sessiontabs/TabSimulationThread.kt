package net.asd.union.handler.sessiontabs

import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.network.INetHandler
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C00PacketKeepAlive
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S00PacketKeepAlive
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Independent simulation thread for a background tab.
 *
 * Each background tab gets its own TabSimulationThread that runs a lightweight
 * loop: process network packets → drain task queue → send heartbeat.
 *
 * IMPORTANT: This thread does NOT tick the world or entities. World ticking
 * (updateEntities/tick) and player ticking (onUpdate) were removed because
 * they block the thread, preventing packet processing. Background tabs don't
 * need world ticking — they only need:
 *   1. Packet processing (via drainPackets) to keep entity state current
 *   2. Keepalive responses (via handleKeepAlive) to prevent timeout
 *   3. Position heartbeats (C03PacketPlayer every 20 ticks) to prevent
 *      server "Timed out" kicks
 *
 * When processing packets, this thread sets a ThreadLocal [currentProcessingRuntime]
 * so that Mixin redirects in NetHandlerPlayClient can return the correct
 * player/world/controller for this tab instead of the active tab's values.
 *
 * This thread does NOT call NetworkManager.processReceivedPackets() because
 * that method calls NetHandlerPlayClient.update() which can block or hang.
 */
class TabSimulationThread(val runtime: LiveTabRuntime) : Thread("TabSim-${runtime.tabId}") {

    @Volatile
    var running = true

    @Volatile
    var paused = false

    private val packetQueue = ConcurrentLinkedQueue<Packet<INetHandler>>()

    /** Target tick interval in milliseconds (~20 TPS for background tabs) */
    private val tickIntervalMs = 50L

    /** Network processing interval in milliseconds (~100Hz for responsiveness) */
    private val networkIntervalMs = 10L

    /** Counter for periodic health logging */
    private var tickCounter = 0L

    /** Timestamp of last successful network activity (keepalive sent/received) */
    @Volatile
    var lastNetworkActivityMs: Long = System.currentTimeMillis()

    fun enqueuePacket(packet: Packet<INetHandler>) {
        packetQueue.add(packet)
    }

    override fun run() {
        // DEBUG: System.err.println("[TabSim][${runtime.tabId}] THREAD run() ENTERED")
        // DEBUG: System.err.flush()

        // Diagnostic: log NM state at startup
        val nm = runtime.networkManager
        val bgNm = runtime.backgroundNetworkManager
        val channelOpen = nm?.isChannelOpen
        val bgChannelOpen = bgNm?.isChannelOpen
        val playerExists = runtime.player != null
        val worldExists = runtime.world != null
        val handlerExists = runtime.currentHandler != null
        val startupInfo = "[TabSim][${runtime.debugLabel()}] Simulation thread started: " +
            "nm=${nm?.let { System.identityHashCode(it) }}, channelOpen=$channelOpen, " +
            "bgNm=${bgNm?.let { System.identityHashCode(it) }}, bgChannelOpen=$bgChannelOpen, " +
            "player=$playerExists, world=$worldExists, handler=$handlerExists, connected=${runtime.connected}"
        // DEBUG: System.err.println(startupInfo)
        // DEBUG: System.err.flush()
        ClientUtils.LOGGER.info(startupInfo)

        var lastTickTime = System.nanoTime()
        var loopIteration = 0L

        try {
            while (running) {
                if (paused) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    continue
                }

                loopIteration++

                // ── STEP 1: Process packets from our queue ──────────────────
                // This is the PRIMARY way incoming packets are handled.
                // All packets for background tabs are routed here by the
                // channelRead0 mixin in MixinNetworkManager.
                runCatching { drainPackets() }.onFailure { e ->
                    ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error in drainPackets", e)
                }

                // ── STEP 2: Drain queued tasks ──────────────────────────────
                runCatching { runtime.drainQueuedTasks(64) }.onFailure { e ->
                    ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error in drainQueuedTasks", e)
                }

                // ── STEP 3: Flush outbound + check connection ───────────────
                // We do NOT call processReceivedPackets() because it calls
                // NetHandlerPlayClient.update() which can block/hang and stall
                // the entire loop. Instead, we just flush outbound packets and
                // check the connection status.
                runCatching { flushOutboundAndCheckConnection() }.onFailure { e ->
                    ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error in flushOutbound", e)
                }

                // Periodic diagnostic every ~10 seconds (assuming ~100 iterations/sec)
                if (loopIteration % 1000 == 0L) {
                    val diagMsg = "[TabSim][${runtime.debugLabel()}] Loop alive: iter=$loopIteration, " +
                        "queueSize=${packetQueue.size}, handler=${runtime.currentHandler != null}, " +
                        "hasWorld=${runtime.hasWorldState}, entities=${runtime.world?.loadedEntityList?.size ?: 0}, " +
                        "players=${runtime.world?.playerEntities?.size ?: 0}"
                    // DEBUG: System.err.println(diagMsg)
                    ClientUtils.LOGGER.info(diagMsg)
                }

                // First-iteration diagnostic: confirm the loop is running
                if (loopIteration == 1L) {
                    val msg = "[TabSim][${runtime.debugLabel()}] First loop iteration: handler=${runtime.currentHandler != null}, queue=${packetQueue.size}, hasWorld=${runtime.hasWorldState}"
                    // DEBUG: System.err.println(msg)
                    ClientUtils.LOGGER.info(msg)
                }

                if (!runtime.hasWorldState) {
                    checkDisconnection()
                    try { Thread.sleep(networkIntervalMs) } catch (_: InterruptedException) {}
                    continue
                }

                // ── World ticking at ~20 TPS ───────────────────────────────────
                val now = System.nanoTime()
                val elapsedSinceLastTick = (now - lastTickTime) / 1_000_000L

                if (elapsedSinceLastTick >= tickIntervalMs) {
                    try {
                        tickOnce()
                    } catch (e: Exception) {
                        ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error in tick", e)
                    }
                    checkDisconnection()
                    lastTickTime = System.nanoTime()
                }

                // Short sleep for network responsiveness
                try { Thread.sleep(networkIntervalMs) } catch (_: InterruptedException) {}
            }
        } catch (e: Throwable) {
            // DEBUG: System.err.println("[TabSim][${runtime.debugLabel()}] FATAL: Simulation thread crashed: ${e.message}")
            // DEBUG: e.printStackTrace(System.err)
            ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] FATAL: Simulation thread crashed", e)
        } finally {
            val stopMsg = "[TabSim][${runtime.debugLabel()}] Simulation thread stopped (running=$running, paused=$paused)"
            // DEBUG: System.err.println(stopMsg)
            ClientUtils.LOGGER.info(stopMsg)
        }
    }

    private fun tickOnce() {
        // Set the ThreadLocal context so Mixin redirects return this tab's state
        currentProcessingRuntime.set(runtime)
        try {
            val player = runtime.player
            val nm = runtime.networkManager

            // 1. Send position + rotation every tick to keep the server
            //    informed of the player's position. Without this, the server
            //    thinks the player is standing still and teleports them back,
            //    causing "physics buggy" behavior.
            //    Use C06PacketPlayerPosLook which includes position + rotation.
            tickCounter++
            if (nm != null && nm.isChannelOpen && player != null) {
                runCatching {
                    nm.sendPacket(C03PacketPlayer.C06PacketPlayerPosLook(
                        player.posX, player.posY, player.posZ,
                        player.rotationYaw, player.rotationPitch,
                        player.onGround
                    ))
                    lastNetworkActivityMs = System.currentTimeMillis()
                }.onFailure { e ->
                    ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Failed to send position update", e)
                }
            }

            // 2. Drain any remaining tasks
            runtime.drainQueuedTasks(64)

            runtime.recordBackgroundTick()

            // Periodic health check: log every 200 ticks (~10 seconds)
            if (tickCounter % 200 == 0L) {
                val channelOpen = nm?.isChannelOpen == true
                val playerPos = player?.let { "%.1f, %.1f, %.1f".format(it.posX, it.posY, it.posZ) } ?: "null"
                val timeSinceNetworkMs = System.currentTimeMillis() - lastNetworkActivityMs
                ClientUtils.LOGGER.info(
                    "[TabSim][${runtime.debugLabel()}] Health: channel=$channelOpen, " +
                    "pos=$playerPos, networkIdle=${timeSinceNetworkMs}ms, " +
                    "queuedPackets=${packetQueue.size}"
                )

                // If no network activity for 60 seconds, the connection is likely dead.
                if (timeSinceNetworkMs > 60_000 && channelOpen) {
                    ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] No network activity for 60s, connection may be dead")
                }
            }
        } finally {
            currentProcessingRuntime.remove()
        }
    }

    /**
     * Checks the connection status without destroying runtime state.
     *
     * Previously this called nm.checkDisconnected() which triggered the
     * fdp$preventCheckDisconnectedOnSimThread mixin, which called
     * runtime.markDisconnectedScreen() — destroying world/player state.
     * This caused the runtime to lose all entity data.
     *
     * Now we just check the channel status directly and update the runtime's
     * connected flag. The sim thread's own checkDisconnection() handles
     * the graceful shutdown without destroying state.
     */
    private fun flushOutboundAndCheckConnection() {
        val nm = runtime.networkManager ?: return

        if (!nm.isChannelOpen) {
            // Channel is closed — just mark as not connected.
            // Do NOT call nm.checkDisconnected() because the mixin
            // intercepts it and calls markDisconnectedScreen() which
            // destroys world/player state.
            if (runtime.connected) {
                runtime.connected = false
                // Also set disconnectedReason so activateRuntime() can tell
                // this tab apart from a tab that was never connected.
                if (runtime.disconnectedReason == null) {
                    runtime.disconnectedReason = "Connection lost"
                }
                ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Network channel closed, marking disconnected (preserving world state)")
            }
            return
        }

        // Flush the outbound packet queue and the Netty channel.
        // Without this, packets sent via nm.sendPacket() (keep-alive responses,
        // heartbeats, etc.) sit in the outbound queue and never reach the server,
        // causing ReadTimeoutException and silent disconnections.
        try {
            flushNetworkManager(nm)
        } catch (e: Exception) {
            ClientUtils.LOGGER.debug("[TabSim][${runtime.debugLabel()}] Error flushing outbound", e)
        }
    }

    private fun drainPackets() {
        var processed = 0
        val queueSizeBefore = packetQueue.size
        while (processed < 512) {
            // Check handler BEFORE polling to prevent packet loss.
            // For tabs in the login phase, currentHandler is a NetHandlerLoginClient.
            // After login succeeds, the NM's handler changes to NetHandlerPlayClient,
            // so we sync currentHandler from the NM before processing each packet.
            syncHandlerFromNetworkManager()
            val handler = runtime.currentHandler
            if (handler == null) {
                if (packetQueue.isNotEmpty()) {
                    val msg = "[TabSim][${runtime.debugLabel()}] drainPackets: handler is null, ${packetQueue.size} packets queued but cannot be processed"
                    ClientUtils.LOGGER.warn(msg)
                }
                break
            }
            val packet = packetQueue.poll() ?: break

            // Handle keepalive packets with maximum priority
            if (packet is S00PacketKeepAlive) {
                handleKeepAlive(packet)
                processed++
                continue
            }

            // Set the ThreadLocal context so Mixin redirects return this tab's
            // player/world/controller instead of the mc singleton's values.
            currentProcessingRuntime.set(runtime)
            try {
                val packetClass = packet.javaClass.simpleName
                val isSpawnPacket = packet is net.minecraft.network.play.server.S0CPacketSpawnPlayer
                    || packet is net.minecraft.network.play.server.S0EPacketSpawnObject
                    || packet is net.minecraft.network.play.server.S0FPacketSpawnMob

                // Diagnostic: log before processing spawn packets
                if (isSpawnPacket) {
                    val worldRef = runtime.world
                    val cwcOnHandler = if (handler is net.minecraft.client.network.NetHandlerPlayClient) {
                        try {
                            val field = handler.javaClass.getDeclaredField("field_147300_g")
                            field.isAccessible = true
                            field.get(handler)?.javaClass?.simpleName ?: "null"
                        } catch (e: Exception) {
                            "error:${e.message}"
                        }
                    } else "not-NHPC"
                    // DEBUG: System.err.println("[TabSim][${runtime.debugLabel()}] BEFORE processPacket($packetClass): runtimeWorld=${worldRef != null}, cwcViaReflection=$cwcOnHandler, threadLocal=${currentProcessingRuntime.get() != null}")
                }

                try {
                    @Suppress("UNCHECKED_CAST")
                    (packet as Packet<INetHandler>).processPacket(handler)
                    // After processing a login packet, the NM's handler may have
                    // changed from NetHandlerLoginClient to NetHandlerPlayClient.
                    // Sync currentHandler so subsequent packets use the new handler.
                    syncHandlerFromNetworkManager()

                    // Fire PacketEvent for background tabs so modules like AutoAccount
                    // can process incoming packets (chat messages, title packets, etc.)
                    // We do this AFTER processPacket so the handler has updated state.
                    // Only fire for relevant packet types to avoid unnecessary overhead.
                    if (packet is net.minecraft.network.play.server.S02PacketChat
                        || packet is net.minecraft.network.play.server.S45PacketTitle
                    ) {
                        try {
                            handleAuthPromptForBackgroundTab(packet)
                        } catch (e: Exception) {
                            ClientUtils.LOGGER.debug("[TabSim][${runtime.debugLabel()}] Error handling auth prompt for ${packet.javaClass.simpleName}", e)
                        }
                    }
                    // Diagnostic: log entity spawn packets processed on sim thread
                    if (isSpawnPacket) {
                        val msg = "[TabSim][${runtime.debugLabel()}] Processed ${packetClass} on sim thread (entities=${runtime.world?.loadedEntityList?.size ?: 0})"
                        // DEBUG: System.err.println(msg)
                        // DEBUG: ClientUtils.LOGGER.info(msg)
                    }
                } catch (e: net.minecraft.network.ThreadQuickExitException) {
                    // ThreadQuickExitException means checkThreadAndEnqueue scheduled
                    // the packet to another thread instead of processing it here.
                    // This should NOT happen on sim threads because MixinPacketThreadUtil
                    // cancels checkThreadAndEnqueue. If it does happen, log it.
                    if (isSpawnPacket) {
                        // DEBUG: System.err.println("[TabSim][${runtime.debugLabel()}] ThreadQuickExitException for $packetClass — checkThreadAndEnqueue was NOT cancelled!")
                        ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] ThreadQuickExitException for $packetClass — checkThreadAndEnqueue was NOT cancelled!")
                    }
                } catch (e: Exception) {
                    ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error processing packet $packetClass", e)
                    if (isSpawnPacket) {
                        // DEBUG: System.err.println("[TabSim][${runtime.debugLabel()}] Exception for $packetClass: ${e.javaClass.simpleName}: ${e.message}")
                        // DEBUG: e.printStackTrace(System.err)
                    }
                }
            } finally {
                currentProcessingRuntime.remove()
            }
            processed++
        }
        if (processed > 0 || queueSizeBefore > 0) {
            ClientUtils.LOGGER.debug("[TabSim][${runtime.debugLabel()}] drainPackets: processed=$processed, queueBefore=$queueSizeBefore, queueAfter=${packetQueue.size}")
        }
    }

    /**
     * Syncs runtime.currentHandler from the NetworkManager's current handler.
     * This is critical for the login→play transition: when a S00PacketLoginSuccess
     * is processed, NetHandlerLoginClient.handleLoginSuccess creates a new
     * NetHandlerPlayClient and sets it as the NM's handler. Without syncing,
     * runtime.currentHandler still points to the old NetHandlerLoginClient,
     * and subsequent play packets would be processed by the wrong handler.
     */
    private fun syncHandlerFromNetworkManager() {
        val nm = runtime.backgroundNetworkManager ?: return
        try {
            val nmHandler = nm.netHandler
            if (nmHandler != null && nmHandler !== runtime.currentHandler) {
                runtime.currentHandler = nmHandler
                ClientUtils.LOGGER.info("[TabSim][${runtime.debugLabel()}] Handler changed: ${nmHandler.javaClass.simpleName}")
            }
        } catch (e: Exception) {
            // NM might be in an inconsistent state during connection close
        }
    }

    /**
     * Handles keepalive packets by sending the response immediately.
     * This bypasses the normal packet event pipeline to ensure the
     * response is never blocked by module event handlers.
     */
    private fun handleKeepAlive(packet: S00PacketKeepAlive) {
        val networkManager = runtime.networkManager
        if (networkManager != null && networkManager.isChannelOpen) {
            runCatching {
                networkManager.sendPacket(C00PacketKeepAlive(packet.func_149134_c()))
                lastNetworkActivityMs = System.currentTimeMillis()
            }.onFailure { e ->
                ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Failed to send keepalive response", e)
            }
        }
    }

    /**
     * Handles auth prompts (e.g., "/login" or "/register" messages) for
     * background tabs by sending the auth command directly through the
     * runtime's NM.
     *
     * This is necessary because modules like AutoAccount listen for PacketEvent
     * to detect auth prompts, but they use mc.thePlayer and addScheduledTask
     * which don't work correctly on the sim thread. Instead of trying to make
     * the full module system work on the sim thread, we handle auth detection
     * and response directly here.
     *
     * The AutoAccount module's state is checked to determine the password
     * and whether auto-register/auto-login are enabled.
     */
    private fun handleAuthPromptForBackgroundTab(packet: Packet<*>) {
        val autoAccount = net.asd.union.features.module.modules.other.AutoAccount
        if (!autoAccount.state) return

        val msg = when (packet) {
            is net.minecraft.network.play.server.S02PacketChat -> {
                packet.chatComponent?.unformattedText?.lowercase(java.util.Locale.ROOT)
                    ?.replace(Regex("\\s+"), " ")?.trim()
            }
            is net.minecraft.network.play.server.S45PacketTitle -> {
                packet.message?.unformattedText?.lowercase(java.util.Locale.ROOT)
                    ?.replace(Regex("\\s+"), " ")?.trim()
            }
            else -> return
        } ?: return

        val nm = runtime.networkManager ?: return
        if (!nm.isChannelOpen) return

        val isRegisterPrompt = "/register" in msg || "register first" in msg ||
            "must register" in msg || "please register" in msg || "you must register" in msg
        val isLoginPrompt = "/login" in msg || "login first" in msg ||
            "log in first" in msg || "must log in" in msg || "must login" in msg ||
            "please login" in msg || "please log in" in msg || "you are not logged in" in msg ||
            "login to this account" in msg || "authenticate" in msg || "log in using" in msg

        if (isRegisterPrompt || isLoginPrompt) {
            // Resolve the password for THIS tab's account, not the active tab.
            // The active tab's password setting is only a fallback when the
            // background tab has no stored password for its own username.
            val accountName = runtime.session.username
            val serverIP = runtime.serverData?.serverIP
            val password = autoAccount.getPasswordForAccount(accountName, serverIP)
            val command = if (isRegisterPrompt) "/register $password $password" else "/login $password"

            // Record the password under THIS tab's account so future joins
            // for the same account re-use the correct password.
            autoAccount.recordPasswordForAccount(serverIP, accountName, password)

            ClientUtils.LOGGER.info("[TabSim][${runtime.debugLabel()}] Auth prompt detected for background tab: " +
                "type=${if (isRegisterPrompt) "REGISTER" else "LOGIN"}, " +
                "account=$accountName, server=${serverIP ?: "?"}, msg=${msg.take(80)}")

            // Send the auth command with a small delay to avoid race conditions
            val nmRef = nm
            Thread({
                try { Thread.sleep(1000L) } catch (_: InterruptedException) {}
                if (nmRef.isChannelOpen) {
                    runCatching {
                        nmRef.sendPacket(C01PacketChatMessage(command))
                        ClientUtils.LOGGER.info("[TabSim][${runtime.debugLabel()}] Sent auth command for background tab: ${command.substringBefore(" ")} *** (account=$accountName)")
                    }.onFailure { e ->
                        ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Failed to send auth command", e)
                    }
                }
            }, "TabSim-Auth-${runtime.tabId}").start()
        }
    }

    private fun checkDisconnection() {
        val nm = runtime.networkManager
        if (nm == null) {
            if (runtime.hasWorldState && runtime.connected) {
                ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] No NetworkManager but still connected with world state, marking disconnected")
                runtime.connected = false
                // IMPORTANT: also set disconnectedReason so activateRuntime()
                // can correctly identify this tab as disconnected (not as
                // "still connecting"). Otherwise the channelOpen check in
                // isStillConnecting would be true (because we have no NM to
                // tell us otherwise) and we'd treat a dead tab as alive.
                if (runtime.disconnectedReason == null) {
                    runtime.disconnectedReason = "Connection lost"
                }
                // Set a disconnect screen so the user sees the reason when they
                // switch to this tab. Without this, the user sees the main menu
                // instead of the disconnect reason.
                if (runtime.currentScreen == null) {
                    val reason = runtime.disconnectedReason ?: "Connection lost"
                    runtime.currentScreen = net.minecraft.client.gui.GuiDisconnected(
                        net.minecraft.client.gui.GuiMultiplayer(net.asd.union.ui.client.gui.GuiMainMenu()),
                        "disconnect.lost",
                        net.minecraft.util.ChatComponentText(reason)
                    )
                }
            }
            return
        }

        if (!nm.isChannelOpen) {
            if (runtime.hasWorldState && runtime.connected) {
                // Channel is closed but we still have world state.
                // Just mark as disconnected — do NOT call markDisconnectedScreen()
                // because that destroys world/player state, which causes the sim
                // thread to lose all entity data and stop processing packets.
                ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Network channel closed while backgrounded (hasWorldState=true), marking disconnected")
                runtime.connected = false
                // IMPORTANT: also set disconnectedReason so activateRuntime()
                // can correctly identify this tab as disconnected (not as
                // "still connecting"). Without this, the runtime's connected
                // flag goes false but disconnectedReason stays null, and the
                // activateRuntime path falls into the "no restorable state"
                // branch with no clean way to tell the difference between
                // "never connected" and "lost connection".
                if (runtime.disconnectedReason == null) {
                    runtime.disconnectedReason = "Connection lost"
                }
                // Set a disconnect screen so the user sees the reason when they
                // switch to this tab. Without this, the user sees the main menu
                // instead of the disconnect reason.
                if (runtime.currentScreen == null) {
                    val reason = runtime.disconnectedReason ?: "Connection lost"
                    runtime.currentScreen = net.minecraft.client.gui.GuiDisconnected(
                        net.minecraft.client.gui.GuiMultiplayer(net.asd.union.ui.client.gui.GuiMainMenu()),
                        "disconnect.lost",
                        net.minecraft.util.ChatComponentText(reason)
                    )
                }
            } else if (!runtime.hasWorldState && runtime.connected) {
                ClientUtils.LOGGER.warn("[TabSim][${runtime.debugLabel()}] Network channel closed, no world state (connected=true)")
                runtime.connected = false
                // See comment above — always set disconnectedReason alongside
                // connected=false so callers can distinguish "never connected"
                // from "lost connection" and so activateRuntime's
                // isStillConnecting check is correct.
                if (runtime.disconnectedReason == null) {
                    runtime.disconnectedReason = "Connection lost"
                }
                // Set a disconnect screen so the user sees the reason when they
                // switch to this tab. Without this, the user sees the main menu
                // instead of the disconnect reason.
                if (runtime.currentScreen == null) {
                    val reason = runtime.disconnectedReason ?: "Connection lost"
                    runtime.currentScreen = net.minecraft.client.gui.GuiDisconnected(
                        net.minecraft.client.gui.GuiMultiplayer(net.asd.union.ui.client.gui.GuiMainMenu()),
                        "disconnect.lost",
                        net.minecraft.util.ChatComponentText(reason)
                    )
                }
            }
        }
    }

    fun shutdown() {
        running = false
        interrupt()
    }

    /**
     * Drains all pending packets from the queue and processes them.
     * Called by activateRuntime() when switching tabs to ensure no
     * packets are lost during the transition.
     */
    fun drainAllPackets() {
        val handler = runtime.currentHandler
        if (handler == null) {
            while (packetQueue.poll() != null) { /* drain */ }
            return
        }
        var processed = 0
        while (true) {
            val packet = packetQueue.poll() ?: break
            try {
                @Suppress("UNCHECKED_CAST")
                (packet as Packet<INetHandler>).processPacket(handler)
                processed++
            } catch (e: Exception) {
                ClientUtils.LOGGER.error("[TabSim][${runtime.debugLabel()}] Error draining packet ${packet.javaClass.simpleName}", e)
            }
        }
        if (processed > 0) {
            ClientUtils.LOGGER.info("[TabSim][${runtime.debugLabel()}] Drained $processed pending packets during activation")
        }
    }

    /**
     * Returns the number of packets currently in the queue.
     */
    fun getPendingPacketCount(): Int = packetQueue.size

    companion object {
        /**
         * ThreadLocal that stores the LiveTabRuntime currently being processed
         * by a TabSimulationThread. Mixin redirects check this ThreadLocal to
         * determine whether to return the runtime's player/world/controller
         * instead of the mc singleton's values.
         */
        private val currentProcessingRuntime = ThreadLocal<LiveTabRuntime?>()

        // Cached reflection for NetworkManager.flushOutboundQueue()
        private var flushOutboundQueueMethod: java.lang.reflect.Method? = null
        private var channelField: java.lang.reflect.Field? = null

        @JvmStatic
        fun getCurrentProcessingRuntime(): LiveTabRuntime? = currentProcessingRuntime.get()

        @JvmStatic
        fun setCurrentProcessingRuntime(runtime: LiveTabRuntime) {
            currentProcessingRuntime.set(runtime)
        }

        @JvmStatic
        fun clearCurrentProcessingRuntime() {
            currentProcessingRuntime.remove()
        }

        /**
         * Flushes the outbound packet queue and Netty channel for a NetworkManager.
         * Uses reflection to access private methods/fields because Kotlin can't
         * resolve the mixin package name directly.
         *
         * Without this flush, packets (keep-alive responses, heartbeats) sit in
         * the outbound queue and never reach the server, causing ReadTimeoutException.
         */
        @JvmStatic
        fun flushNetworkManager(nm: net.minecraft.network.NetworkManager) {
            // Flush the outbound queue (writes queued packets to the Netty channel)
            val method = flushOutboundQueueMethod ?: run {
                val m = nm.javaClass.getDeclaredMethod("flushOutboundQueue")
                m.isAccessible = true
                flushOutboundQueueMethod = m
                m
            }
            method.invoke(nm)

            // Flush the Netty channel (actually sends data over the network)
            val field = channelField ?: run {
                val f = nm.javaClass.getDeclaredField("channel")
                f.isAccessible = true
                channelField = f
                f
            }
            val channel = field.get(nm) as? io.netty.channel.Channel?
            channel?.flush()
        }
    }
}
