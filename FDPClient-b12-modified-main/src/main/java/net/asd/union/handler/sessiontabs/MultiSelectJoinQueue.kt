package net.asd.union.handler.sessiontabs

import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.util.Session
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Manages a sequential queue of accounts that should be connected to a server,
 * each in its own background tab via LiveTabRuntimeManager.connectRuntime.
 *
 * This runs entirely in the background — the user stays on their current tab
 * and never sees a tab switch or screen change.
 *
 * Flow:
 * 1. All tabs are created upfront with WAITING status (yellow)
 * 2. For each account:
 *    a. Create an offline session
 *    b. Call connectRuntime to establish a detached background connection
 *    c. Wait for onTabConnected callback (from LiveTabRuntimeManager)
 *    d. Wait 1 second settle time
 *    e. Set tab status to NORMAL (white)
 * 3. Move to the next account
 */
object MultiSelectJoinQueue : MinecraftInstance {

    data class QueuedAccount(
        val username: String,
        var tabId: String? = null,
        var retries: Int = 0
    )

    private val queue = ArrayDeque<QueuedAccount>()
    private var targetServerData: ServerData? = null
    private var processing: QueuedAccount? = null
    private var state: QueueState = QueueState.IDLE
    private var waitUntil: Long = 0
    private var successfulConnections = 0

    private const val JOIN_SETTLE_MS = 3000L
    private const val INTER_ACCOUNT_DELAY_MS = 5000L
    private const val INITIAL_DELAY_MS = 5000L
    private const val MAX_RETRIES = 3

    enum class QueueState {
        IDLE,
        INITIAL_DELAY,
        CONNECTING,
        WAITING_SETTLE,
        WAITING_NEXT
    }

    fun isRunning(): Boolean = state != QueueState.IDLE

    fun getQueueSize(): Int = queue.size

    fun getCurrentAccount(): QueuedAccount? = processing

    fun getQueueSnapshot(): List<QueuedAccount> = queue.toList()

    /**
     * Enqueue accounts and start processing via background connectRuntime.
     */
    fun enqueue(accounts: List<String>, serverData: ServerData) {
        if (accounts.isEmpty()) return

        targetServerData = ServerData(serverData.serverName, serverData.serverIP, false)

        for (username in accounts) {
            if (queue.none { it.username == username } && processing?.username != username) {
                val tabId = ClientTabManager.createOfflineTabBackground(username)
                if (tabId != null) {
                    ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.WAITING, "Waiting in queue for ${serverData.serverIP}")
                    queue.addLast(QueuedAccount(username, tabId))
                } else {
                    ClientUtils.displayAlert("Multi-select: Failed to create tab for $username. Skipping.")
                }
            }
        }

        if (queue.isEmpty()) return

        ClientUtils.displayAlert("Multi-select: ${queue.size} account(s) queued for ${serverData.serverIP}")
        ClientTabManager.setTabBarVisible(true)

        if (state == QueueState.IDLE) {
            // Wait for the first account (the one that joined naturally) to settle
            state = QueueState.INITIAL_DELAY
            waitUntil = System.currentTimeMillis() + INITIAL_DELAY_MS
            ClientUtils.displayAlert("Multi-select: Waiting ${INITIAL_DELAY_MS / 1000}s for first account to settle...")
        }
    }

    fun cancel() {
        for (queued in queue) {
            queued.tabId?.let { ClientTabManager.setTabStatus(it, ClientTabManager.TabStatus.NORMAL) }
        }
        processing?.tabId?.let { ClientTabManager.setTabStatus(it, ClientTabManager.TabStatus.NORMAL) }
        queue.clear()
        processing = null
        state = QueueState.IDLE
        waitUntil = 0
        successfulConnections = 0
        ClientUtils.displayAlert("Multi-select queue cancelled.")
    }

    /**
     * Called every game tick to drive the queue forward.
     */
    fun tick() {
        if (state == QueueState.IDLE) return

        when (state) {
            QueueState.INITIAL_DELAY -> {
                if (System.currentTimeMillis() >= waitUntil) {
                    processNext()
                }
            }
            QueueState.WAITING_SETTLE -> {
                if (System.currentTimeMillis() >= waitUntil) {
                    processing?.tabId?.let {
                        ClientTabManager.setTabStatus(it, ClientTabManager.TabStatus.NORMAL)
                    }
                    processing = null
                    // Wait between accounts to avoid rate limiting
                    state = QueueState.WAITING_NEXT
                    waitUntil = System.currentTimeMillis() + INTER_ACCOUNT_DELAY_MS
                }
            }
            QueueState.WAITING_NEXT -> {
                if (System.currentTimeMillis() >= waitUntil) {
                    processNext()
                }
            }
            else -> {}
        }
    }

    /**
     * Called by LiveTabRuntimeManager when a background tab successfully joins a server.
     */
    fun onTabConnected(tabId: String) {
        val current = processing ?: return
        if (current.tabId == tabId) {
            successfulConnections++
            state = QueueState.WAITING_SETTLE
            waitUntil = System.currentTimeMillis() + JOIN_SETTLE_MS
            ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.WAITING, "Connected — settling...")
            ClientUtils.displayAlert("Multi-select: ${current.username} joined. Settling...")
        }
    }

    /**
     * Called by LiveTabRuntimeManager when a background tab disconnects.
     */
    fun onTabDisconnected(tabId: String) {
        val current = processing ?: return
        if (current.tabId == tabId) {
            current.retries++
            if (current.retries <= MAX_RETRIES) {
                ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.WAITING, "Disconnected — retry ${current.retries}/$MAX_RETRIES")
                queue.addLast(current.copy(tabId = tabId))
                ClientUtils.displayAlert("Multi-select: ${current.username} disconnected. Re-queued (retry ${current.retries}/$MAX_RETRIES).")
            } else {
                ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.NORMAL)
                ClientUtils.displayAlert("Multi-select: ${current.username} failed after $MAX_RETRIES retries. Skipping.")
            }
            processing = null
            state = QueueState.WAITING_NEXT
            waitUntil = System.currentTimeMillis() + INTER_ACCOUNT_DELAY_MS
        }
    }

    private fun processNext() {
        if (queue.isEmpty()) {
            state = QueueState.IDLE
            processing = null
            val server = targetServerData
            if (server != null) {
                if (successfulConnections > 0) {
                    ClientUtils.displayAlert("Multi-select queue complete. $successfulConnections account(s) connected to ${server.serverIP}.")
                } else {
                    ClientUtils.displayAlert("Multi-select queue complete. No accounts successfully connected to ${server.serverIP}.")
                }
            }
            successfulConnections = 0
            return
        }

        val serverData = targetServerData ?: run {
            ClientUtils.displayAlert("Multi-select: No target server. Cancelling.")
            cancel()
            return
        }

        val next = queue.removeFirst()
        processing = next
        state = QueueState.CONNECTING

        val session = createOfflineSession(next.username)
        val tabId = next.tabId ?: run {
            ClientUtils.displayAlert("Multi-select: No tab for ${next.username}. Skipping.")
            state = QueueState.WAITING_NEXT
            return
        }

        ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.WAITING, "Connecting to ${serverData.serverIP}...")

        val connected = LiveTabRuntimeManager.connectRuntime(tabId, session, serverData)
        if (!connected) {
            ClientUtils.displayAlert("Multi-select: Failed to connect ${next.username}. Re-queuing.")
            next.retries++
            if (next.retries <= MAX_RETRIES) {
                ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.WAITING, "Failed — retry ${next.retries}/$MAX_RETRIES")
                queue.addLast(next)
            } else {
                ClientTabManager.setTabStatus(tabId, ClientTabManager.TabStatus.NORMAL)
            }
            processing = null
            state = QueueState.WAITING_NEXT
        } else {
            ClientUtils.displayAlert("Multi-select: Connecting ${next.username} to ${serverData.serverIP} (background)...")
        }
    }

    private fun createOfflineSession(username: String): Session {
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
        return Session(username, uuid.toString(), "-", "legacy")
    }
}
