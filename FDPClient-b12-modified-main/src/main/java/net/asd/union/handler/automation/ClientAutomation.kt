package net.asd.union.handler.automation

import net.asd.union.event.EventManager
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.util.Session
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ClientAutomation — a tool-calling system that allows code to programmatically
 * control the Minecraft client. Actions are queued and executed sequentially
 * on the client thread, driving the client through its actual GUI flow.
 *
 * Available tools:
 * - SwitchTab: switches to a specific tab
 * - SetSession: sets the current offline session (username)
 * - ConnectToServer: connects to a server via GuiConnecting (vanilla flow)
 * - WaitUntilConnected: waits until the player is in a world
 * - Delay: waits a specified number of milliseconds
 * - DisplayScreen: shows a specific GUI screen
 */
object ClientAutomation : MinecraftInstance {

    // ==================== Action definitions ====================

    sealed class AutomationAction {
        /** Switch to an existing tab by ID */
        data class SwitchTab(val tabId: String) : AutomationAction()

        /** Set the current session to an offline account */
        data class SetSession(val username: String) : AutomationAction()

        /** Connect to a server using the vanilla GuiConnecting flow */
        data class ConnectToServer(val serverIp: String, val serverName: String = "") : AutomationAction()

        /** Wait until the player is connected to a world (in-game) */
        data class WaitUntilConnected(val timeoutMs: Long = 30000L) : AutomationAction()

        /** Wait for a specified duration */
        data class Delay(val ms: Long) : AutomationAction()

        /** Display a specific GUI screen */
        data class DisplayScreen(val screenProvider: () -> net.minecraft.client.gui.GuiScreen?) : AutomationAction()

        /** Run an arbitrary block of code */
        data class Run(val name: String, val block: () -> Unit) : AutomationAction()
    }

    // ==================== State ====================

    private val actionQueue = ConcurrentLinkedQueue<AutomationAction>()
    private var currentAction: AutomationAction? = null
    private var state: AutomationState = AutomationState.IDLE
    private var waitStartMs: Long = 0
    private var waitTargetMs: Long = 0
    private var onActionComplete: (() -> Unit)? = null

    enum class AutomationState {
        IDLE,
        EXECUTING,
        WAITING_DELAY,
        WAITING_CONNECTION
    }

    // ==================== Public API ====================

    fun isRunning(): Boolean = state != AutomationState.IDLE

    fun getQueueSize(): Int = actionQueue.size

    fun getCurrentAction(): AutomationAction? = currentAction

    /**
     * Enqueue a single action.
     */
    fun enqueue(action: AutomationAction) {
        actionQueue.add(action)
        if (state == AutomationState.IDLE) {
            processNext()
        }
    }

    /**
     * Enqueue multiple actions in order.
     */
    fun enqueueAll(actions: List<AutomationAction>) {
        actionQueue.addAll(actions)
        if (state == AutomationState.IDLE) {
            processNext()
        }
    }

    /**
     * Cancel all pending actions and reset state.
     */
    fun cancel() {
        actionQueue.clear()
        currentAction = null
        state = AutomationState.IDLE
        waitStartMs = 0
        waitTargetMs = 0
        onActionComplete = null
    }

    /**
     * Called every game tick to drive the automation forward.
     */
    fun tick() {
        if (state == AutomationState.IDLE) return

        when (state) {
            AutomationState.WAITING_DELAY -> {
                if (System.currentTimeMillis() - waitStartMs >= waitTargetMs) {
                    state = AutomationState.EXECUTING
                    processNext()
                }
            }
            AutomationState.WAITING_CONNECTION -> {
                val elapsed = System.currentTimeMillis() - waitStartMs
                if (mc.thePlayer != null && mc.theWorld != null) {
                    // Connected!
                    state = AutomationState.EXECUTING
                    processNext()
                } else if (elapsed >= (currentAction as? AutomationAction.WaitUntilConnected)?.timeoutMs ?: 30000L) {
                    // Timed out
                    ClientUtils.displayAlert("Automation: Connection timed out.")
                    state = AutomationState.EXECUTING
                    processNext()
                }
            }
            else -> {}
        }
    }

    // ==================== Action processing ====================

    private fun processNext() {
        val next = actionQueue.poll()
        if (next == null) {
            currentAction = null
            state = AutomationState.IDLE
            onActionComplete?.invoke()
            onActionComplete = null
            return
        }

        currentAction = next
        executeAction(next)
    }

    private fun executeAction(action: AutomationAction) {
        when (action) {
            is AutomationAction.SwitchTab -> {
                val success = ClientTabManager.switchToTabById(action.tabId)
                if (!success) {
                    ClientUtils.displayAlert("Automation: Tab ${action.tabId} not found.")
                }
                processNext()
            }

            is AutomationAction.SetSession -> {
                val session = createOfflineSession(action.username)
                mc.session = session
                EventManager.call(SessionUpdateEvent)
                ClientUtils.displayAlert("Automation: Session set to ${action.username}")
                processNext()
            }

            is AutomationAction.ConnectToServer -> {
                val serverData = ServerData(action.serverName, action.serverIp, false)
                mc.displayGuiScreen(GuiConnecting(GuiMainMenu(), mc, serverData))
                // Now we need to wait for the connection
                state = AutomationState.WAITING_CONNECTION
                waitStartMs = System.currentTimeMillis()
            }

            is AutomationAction.WaitUntilConnected -> {
                if (mc.thePlayer != null && mc.theWorld != null) {
                    // Already connected
                    processNext()
                } else {
                    state = AutomationState.WAITING_CONNECTION
                    waitStartMs = System.currentTimeMillis()
                }
            }

            is AutomationAction.Delay -> {
                state = AutomationState.WAITING_DELAY
                waitStartMs = System.currentTimeMillis()
                waitTargetMs = action.ms
            }

            is AutomationAction.DisplayScreen -> {
                val screen = action.screenProvider()
                if (screen != null) {
                    mc.displayGuiScreen(screen)
                }
                processNext()
            }

            is AutomationAction.Run -> {
                try {
                    action.block()
                } catch (e: Exception) {
                    ClientUtils.displayAlert("Automation: Error in ${action.name}: ${e.message}")
                }
                processNext()
            }
        }
    }

    private fun createOfflineSession(username: String): Session {
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
        return Session(username, uuid.toString(), "-", "legacy")
    }
}
