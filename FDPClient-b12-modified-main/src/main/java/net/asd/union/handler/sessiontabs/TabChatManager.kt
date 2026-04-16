package net.asd.union.handler.sessiontabs

import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.util.IChatComponent

object TabChatManager : MinecraftInstance {

    private const val MAX_HISTORY = 200

    private val histories = linkedMapOf<String, MutableList<ChatEntry>>()
    private var restoringHistory = false

    fun capturePrintedMessage(component: IChatComponent, lineId: Int) {
        if (restoringHistory) {
            return
        }

        record(resolveContextTabId(), component, lineId)
    }

    fun recordDetachedChatMessage(component: IChatComponent, lineId: Int) {
        record(resolveContextTabId(), component, lineId)
    }

    fun restoreForTab(tabId: String?) {
        val chatGui = mc.ingameGUI.chatGUI

        restoringHistory = true
        try {
            chatGui.clearChatMessages()

            histories[tabId]?.forEach { entry ->
                chatGui.printChatMessageWithOptionalDeletion(entry.component.createCopy(), entry.lineId)
            }

            chatGui.resetScroll()
        } finally {
            restoringHistory = false
        }
    }

    fun clearTab(tabId: String) {
        histories.remove(tabId)
    }

    private fun resolveContextTabId(): String? {
        return SessionRuntimeScope.currentRuntime()?.tabId ?: ClientTabManager.currentTabId()
    }

    private fun record(tabId: String?, component: IChatComponent, lineId: Int) {
        if (tabId == null) {
            return
        }

        val history = histories.getOrPut(tabId) { mutableListOf() }

        if (lineId != 0) {
            history.removeAll { it.lineId == lineId }
        }

        history += ChatEntry(component.createCopy(), lineId)

        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    private data class ChatEntry(
        val component: IChatComponent,
        val lineId: Int
    )
}
