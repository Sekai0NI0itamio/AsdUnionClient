package net.asd.union.handler.sessiontabs

import net.asd.union.file.FileManager
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Per-tab chat history manager with file-based persistence.
 *
 * Each account tab has its own chat history file that records all chat messages
 * in raw (JSON-serialized IChatComponent). When the user switches tabs, the
 * current tab's chat is saved to its file, the GuiNewChat is cleared, and the
 * new tab's chat is restored from its file.
 *
 * This ensures chat messages from one account tab are never rendered in another
 * account tab's chat view.
 */
object TabChatManager : MinecraftInstance {

    private const val MAX_HISTORY = 500
    private const val CHAT_DIR_NAME = "ChatHistory"
    private const val SAVE_INTERVAL_MS = 5000L

    /** In-memory cache of chat entries per tab, for fast access */
    private val histories = linkedMapOf<String, MutableList<ChatEntry>>()

    /** Tracks which tabs have unsaved changes */
    private val dirtyTabs = mutableSetOf<String>()

    /** Last time we flushed dirty tabs to disk */
    private var lastSaveTime = 0L

    /**
     * When true, messages being replayed into GuiNewChat during tab restore
     * should NOT be re-recorded into the history.
     */
    @Volatile
    private var restoringHistory = false

    /**
     * Queue of chat messages from background tab simulation threads.
     * These are recorded to the history but never displayed on the foreground.
     */
    private val backgroundChatQueue = ConcurrentLinkedQueue<Pair<String, ChatEntry>>()

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Called from MixinGuiNewChat TAIL injection when a chat message is printed
     * on the main thread. Records the message to the current active tab's history.
     */
    fun capturePrintedMessage(component: IChatComponent, lineId: Int) {
        if (restoringHistory) return
        record(resolveContextTabId(), component, lineId)
    }

    /**
     * Called from MixinNetHandlerPlayClient when a chat packet arrives on a
     * TabSimulationThread. Records the message to the background tab's history
     * without displaying it on the foreground.
     */
    fun captureBackgroundChat(tabId: String, component: IChatComponent, lineId: Int) {
        val entry = ChatEntry(serializeComponent(component), lineId, System.currentTimeMillis())
        backgroundChatQueue.add(tabId to entry)
    }

    /**
     * Process any queued background chat messages. Should be called on the
     * main thread periodically (e.g., during tick).
     */
    fun drainBackgroundChatQueue() {
        var processed = 0
        while (processed < 50) { // Limit per tick to avoid lag spikes
            val pair = backgroundChatQueue.poll() ?: break
            val (tabId, entry) = pair
            val history = histories.getOrPut(tabId) { loadHistoryFromFile(tabId) }
            addEntryToHistory(history, entry)
            dirtyTabs.add(tabId)
            processed++
        }

        // Periodically flush dirty tabs to disk
        val now = System.currentTimeMillis()
        if (dirtyTabs.isNotEmpty() && now - lastSaveTime >= SAVE_INTERVAL_MS) {
            flushDirtyTabs()
            lastSaveTime = now
        }
    }

    /**
     * Save the current foreground tab's chat state and switch to the new tab.
     * Called from ClientTabManager.switchToTab().
     */
    fun saveCurrentAndSwitch(fromTabId: String?, toTabId: String?) {
        // Flush all dirty tabs to file before switching
        flushDirtyTabs()

        // Save current tab's chat to file
        if (fromTabId != null) {
            saveHistoryToFile(fromTabId, histories[fromTabId] ?: mutableListOf())
        }

        // Clear the in-game chat
        restoringHistory = true
        try {
            mc.ingameGUI.chatGUI.clearChatMessages()
        } finally {
            restoringHistory = false
        }

        // Restore new tab's chat
        restoreForTab(toTabId)
    }

    /**
     * Restore chat history for the given tab from file/memory into GuiNewChat.
     */
    fun restoreForTab(tabId: String?) {
        if (tabId == null) return

        val history = histories.getOrPut(tabId) { loadHistoryFromFile(tabId) }

        restoringHistory = true
        try {
            mc.ingameGUI.chatGUI.clearChatMessages()

            for (entry in history) {
                val component = deserializeComponent(entry.rawJson)
                if (component != null) {
                    mc.ingameGUI.chatGUI.printChatMessageWithOptionalDeletion(component, entry.lineId)
                }
            }

            mc.ingameGUI.chatGUI.resetScroll()
        } finally {
            restoringHistory = false
        }
    }

    /**
     * Remove all chat history for a tab (when the tab is closed).
     */
    fun clearTab(tabId: String) {
        histories.remove(tabId)
        dirtyTabs.remove(tabId)
        getChatFile(tabId).delete()
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun resolveContextTabId(): String? {
        return SessionRuntimeScope.currentRuntime()?.tabId ?: ClientTabManager.currentTabId()
    }

    private fun record(tabId: String?, component: IChatComponent, lineId: Int) {
        if (tabId == null) return

        val history = histories.getOrPut(tabId) { loadHistoryFromFile(tabId) }
        val entry = ChatEntry(serializeComponent(component), lineId, System.currentTimeMillis())
        addEntryToHistory(history, entry)
        dirtyTabs.add(tabId)
    }

    private fun addEntryToHistory(history: MutableList<ChatEntry>, entry: ChatEntry) {
        // If lineId != 0, remove previous entry with same lineId (action bar updates)
        if (entry.lineId != 0) {
            history.removeAll { it.lineId == entry.lineId }
        }

        history += entry

        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    private fun flushDirtyTabs() {
        val toFlush = dirtyTabs.toList()
        dirtyTabs.clear()
        for (tabId in toFlush) {
            val history = histories[tabId] ?: continue
            saveHistoryToFile(tabId, history)
        }
    }

    // ── File persistence ──────────────────────────────────────────────────

    private fun getChatDir(): File {
        val dir = File(FileManager.dir, CHAT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getChatFile(tabId: String): File {
        // Sanitize tabId for use as filename
        val safeName = tabId.replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
        return File(getChatDir(), "chat_$safeName.json")
    }

    private fun saveHistoryToFile(tabId: String, history: MutableList<ChatEntry>) {
        try {
            val file = getChatFile(tabId)
            val json = gson.toJson(history.map { mapOf(
                "rawJson" to it.rawJson,
                "lineId" to it.lineId,
                "timestamp" to it.timestamp
            )})
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            ClientUtils.LOGGER.error("[TabChatManager] Failed to save chat history for tab $tabId", e)
        }
    }

    private fun loadHistoryFromFile(tabId: String): MutableList<ChatEntry> {
        try {
            val file = getChatFile(tabId)
            if (!file.exists()) return mutableListOf()

            val json = file.readText(Charsets.UTF_8)
            val list = gson.fromJson(json, List::class.java) as? List<Map<String, Any>> ?: return mutableListOf()

            return list.mapNotNull { map ->
                val rawJson = map["rawJson"] as? String ?: return@mapNotNull null
                val lineId = (map["lineId"] as? Number)?.toInt() ?: 0
                val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                ChatEntry(rawJson, lineId, timestamp)
            }.toMutableList()
        } catch (e: Exception) {
            ClientUtils.LOGGER.error("[TabChatManager] Failed to load chat history for tab $tabId", e)
            return mutableListOf()
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────

    private val gson = com.google.gson.Gson()

    private fun serializeComponent(component: IChatComponent): String {
        return try {
            net.minecraft.util.IChatComponent.Serializer.componentToJson(component)
        } catch (e: Exception) {
            // Fallback: serialize as plain text
            try {
                gson.toJson(mapOf("text" to component.formattedText))
            } catch (e2: Exception) {
                "{}"
            }
        }
    }

    private fun deserializeComponent(rawJson: String): IChatComponent? {
        return try {
            net.minecraft.util.IChatComponent.Serializer.jsonToComponent(rawJson)
        } catch (e: Exception) {
            // Fallback: try to extract text and create a simple component
            try {
                val map = gson.fromJson(rawJson, Map::class.java) as? Map<*, *>
                val text = map?.get("text") as? String
                if (text != null) ChatComponentText(text) else null
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * A chat entry stored in history. Uses rawJson (serialized IChatComponent)
     * instead of a live IChatComponent object to avoid memory leaks and ensure
     * safe cross-thread storage.
     */
    private data class ChatEntry(
        val rawJson: String,
        val lineId: Int,
        val timestamp: Long
    )
}
