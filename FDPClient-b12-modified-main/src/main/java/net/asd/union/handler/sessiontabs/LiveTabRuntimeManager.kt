package net.asd.union.handler.sessiontabs

import net.asd.union.features.module.modules.client.Rotations
import net.asd.union.injection.forge.mixins.gui.MixinGuiConnectingAccessor
import net.asd.union.ui.client.gui.GuiMainMenu
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.rotation
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationUtils
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.multiplayer.PlayerControllerMP
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.network.NetHandlerPlayClient
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraft.network.INetHandler
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent
import net.minecraft.util.MovementInput
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Session
import java.util.concurrent.ConcurrentLinkedQueue

private fun sanitizeScreen(screen: GuiScreen?): GuiScreen? {
    return if (screen?.doesGuiPauseGame() == true) null else screen
}

object SessionRuntimeScope : MinecraftInstance {

    private val detachedRuntime = ThreadLocal<LiveTabRuntime?>()
    private val detachedKeyStates = ThreadLocal<Map<KeyBinding, KeyBindingState>?>()

    fun isDetachedContextActive() = detachedRuntime.get() != null

    fun currentRuntime(): LiveTabRuntime? = detachedRuntime.get()

    fun resolveDetachedKeyState(binding: KeyBinding): Boolean? {
        return detachedKeyStates.get()?.get(binding)?.pressed
    }

    fun <T> runDetached(runtime: LiveTabRuntime, clearScreen: Boolean = true, block: () -> T): T {
        val snapshot = MinecraftStateSnapshot.capture()
        val inputSnapshot = DetachedInputSnapshot.capture()
        val actionSnapshot = DetachedActionSnapshot.capture()
        detachedRuntime.set(runtime)

        return try {
            snapshot.applyRuntime(runtime, clearScreen = clearScreen)
            inputSnapshot.applyFor(runtime)
            actionSnapshot.suppressForBackgroundRuntime(runtime)
            block()
        } finally {
            try {
                snapshot.syncRuntime(runtime)
            } finally {
                snapshot.restore()
                inputSnapshot.restore()
                actionSnapshot.restore()
                detachedRuntime.remove()
            }
        }
    }

    fun resetSharedActionStateForCurrentPlayer() {
        applyNeutralActionState(mc.thePlayer?.rotation ?: Rotation.ZERO)
    }

    private fun extractManagedNetworkManager(screen: GuiScreen?): NetworkManager? {
        val connecting = screen as? net.minecraft.client.multiplayer.GuiConnecting ?: return null
        return (connecting as? MixinGuiConnectingAccessor)?.networkManager
    }

    private data class MinecraftStateSnapshot(
        val session: Session,
        val world: WorldClient?,
        val player: EntityPlayerSP?,
        val playerController: PlayerControllerMP?,
        val serverData: ServerData?,
        val renderViewEntity: Entity?,
        val objectMouseOver: MovingObjectPosition?,
        val pointedEntity: Entity?,
        val currentScreen: GuiScreen?
    ) {
        companion object : MinecraftInstance {
            fun capture() = MinecraftStateSnapshot(
                session = mc.session,
                world = mc.theWorld,
                player = mc.thePlayer,
                playerController = mc.playerController,
                serverData = mc.currentServerData,
                renderViewEntity = mc.renderViewEntity,
                objectMouseOver = mc.objectMouseOver,
                pointedEntity = mc.pointedEntity,
                currentScreen = mc.currentScreen
            )
        }

        fun applyRuntime(runtime: LiveTabRuntime, clearScreen: Boolean) {
            mc.session = runtime.session
            mc.theWorld = runtime.world
            mc.thePlayer = runtime.player
            mc.playerController = runtime.playerController
            mc.renderViewEntity = runtime.renderViewEntity ?: runtime.player
            mc.objectMouseOver = null
            mc.pointedEntity = null
            mc.setServerData(runtime.serverData)
            mc.currentScreen = if (clearScreen) null else sanitizeScreen(runtime.currentScreen)
        }

        fun syncRuntime(runtime: LiveTabRuntime) {
            runtime.session = mc.session
            runtime.world = mc.theWorld
            runtime.player = mc.thePlayer
            runtime.playerController = mc.playerController
            runtime.serverData = mc.currentServerData
            runtime.renderViewEntity = mc.renderViewEntity ?: mc.thePlayer
            runtime.currentScreen = sanitizeScreen(mc.currentScreen)
            runtime.backgroundNetworkManager = extractManagedNetworkManager(mc.currentScreen)
                ?: mc.thePlayer?.sendQueue?.networkManager
                ?: runtime.backgroundNetworkManager
        }

        fun restore() {
            mc.session = session
            mc.theWorld = world
            mc.thePlayer = player
            mc.playerController = playerController
            mc.renderViewEntity = renderViewEntity
            mc.objectMouseOver = objectMouseOver
            mc.pointedEntity = pointedEntity
            mc.currentScreen = currentScreen
            mc.setServerData(serverData)
        }
    }

    private class DetachedInputSnapshot(private val keyStates: List<KeyBindingState>) {
        fun applyFor(runtime: LiveTabRuntime) {
            val linkedKeyStates = ClientTabManager.prepareDetachedKeyStates(runtime)
            val shouldMirrorInput = ClientTabManager.shouldMirrorMainInputTo(runtime.tabId)
            val desiredStates = if (linkedKeyStates != null) {
                linkedKeyStates.mapValues { (binding, pressed) ->
                    KeyBindingState(binding, pressed, if (pressed) 1 else 0)
                }
            } else if (shouldMirrorInput) {
                keyStates.associateBy { it.binding }
            } else {
                emptyMap()
            }

            detachedKeyStates.set(desiredStates)

            keyStates.forEach { state ->
                val desiredState = desiredStates[state.binding]
                state.binding.pressed = desiredState?.pressed ?: false
                state.binding.pressTime = desiredState?.pressTime ?: 0
            }

            mc.thePlayer?.movementInput?.apply {
                moveForward = 0f
                moveStrafe = 0f
                jump = desiredStates[mc.gameSettings.keyBindJump]?.pressed == true
                sneak = desiredStates[mc.gameSettings.keyBindSneak]?.pressed == true
            }
        }

        fun restore() {
            detachedKeyStates.remove()
            keyStates.forEach { it.restore() }
        }

        companion object : MinecraftInstance {
            fun capture(): DetachedInputSnapshot {
                val settings = mc.gameSettings
                val keyStates = settings?.keyBindings
                    ?.asSequence()
                    ?.filterNotNull()
                    ?.distinct()
                    ?.map { KeyBindingState(it, it.pressed, it.pressTime) }
                    ?.toList()
                    .orEmpty()
                return DetachedInputSnapshot(keyStates)
            }
        }
    }

    private class DetachedActionSnapshot(
        private val targetRotation: Rotation?,
        private val currentRotation: Rotation?,
        private val serverRotation: Rotation,
        private val lastRotations: List<Rotation>,
        private val modifiedInput: MovementInput,
        private val activeSettings: net.asd.union.utils.rotation.RotationSettings?,
        private val resetTicks: Int,
        private val prevHeadPitch: Float,
        private val headPitch: Float
    ) {
        fun suppressForBackgroundRuntime(runtime: LiveTabRuntime) {
            applyNeutralActionState(runtime.player?.rotation ?: Rotation.ZERO)
        }

        fun restore() {
            RotationUtils.targetRotation = targetRotation
            RotationUtils.currentRotation = currentRotation
            RotationUtils.serverRotation = serverRotation
            RotationUtils.lastRotations = lastRotations.toMutableList()
            RotationUtils.modifiedInput = modifiedInput
            RotationUtils.activeSettings = activeSettings
            RotationUtils.resetTicks = resetTicks
            Rotations.prevHeadPitch = prevHeadPitch
            Rotations.headPitch = headPitch
        }

        companion object {
            fun capture() = DetachedActionSnapshot(
                targetRotation = RotationUtils.targetRotation?.copy(),
                currentRotation = RotationUtils.currentRotation?.copy(),
                serverRotation = RotationUtils.serverRotation.copy(),
                lastRotations = RotationUtils.lastRotations.map { it.copy() },
                modifiedInput = MovementInput().apply {
                    moveForward = RotationUtils.modifiedInput.moveForward
                    moveStrafe = RotationUtils.modifiedInput.moveStrafe
                    jump = RotationUtils.modifiedInput.jump
                    sneak = RotationUtils.modifiedInput.sneak
                },
                activeSettings = RotationUtils.activeSettings,
                resetTicks = RotationUtils.resetTicks,
                prevHeadPitch = Rotations.prevHeadPitch,
                headPitch = Rotations.headPitch
            )
        }
    }

    private fun applyNeutralActionState(baseRotation: Rotation) {
        RotationUtils.targetRotation = null
        RotationUtils.currentRotation = null
        RotationUtils.serverRotation = baseRotation.copy()
        RotationUtils.lastRotations = MutableList(RotationUtils.lastRotations.size.coerceAtLeast(1)) {
            baseRotation.copy()
        }
        RotationUtils.modifiedInput = MovementInput()
        RotationUtils.activeSettings = null
        RotationUtils.resetTicks = 0
        Rotations.prevHeadPitch = baseRotation.pitch
        Rotations.headPitch = baseRotation.pitch
    }

    private data class KeyBindingState(
        val binding: KeyBinding,
        val pressed: Boolean,
        val pressTime: Int
    ) {
        fun restore() {
            binding.pressed = pressed
            binding.pressTime = pressTime
        }
    }
}

object LiveTabRuntimeManager : MinecraftInstance {

    private val runtimes = linkedMapOf<String, LiveTabRuntime>()
    private var activeRuntimeTabId: String? = null
    private var backgroundTickCarryNanos = 0L
    private var lastBackgroundTickNanos = System.nanoTime()

    private const val BACKGROUND_TICK_NANOS = 50_000_000L
    private const val MAX_CATCH_UP_STEPS = 4

    fun hasRuntime(tabId: String) = runtimes[tabId]?.hasRestorableState == true

    fun runtimeFor(tabId: String): LiveTabRuntime? = runtimes[tabId]

    fun deactivateRuntime(tabId: String?) {
        if (activeRuntimeTabId == tabId) {
            activeRuntimeTabId = null
        }
    }

    fun prepareRuntimeForBackground(tabId: String) {
        val runtime = runtimes[tabId] ?: return
        runtime.currentScreen = sanitizeScreen(runtime.currentScreen)
    }

    fun captureCurrentRuntime(tabId: String): Boolean {
        val runtime = runtimes.getOrPut(tabId) { LiveTabRuntime(tabId) }
        runtime.session = mc.session
        runtime.world = mc.theWorld
        runtime.player = mc.thePlayer
        runtime.playerController = mc.playerController
        runtime.serverData = mc.currentServerData
        runtime.renderViewEntity = mc.renderViewEntity ?: mc.thePlayer
        runtime.currentScreen = sanitizeScreen(mc.currentScreen)
        runtime.backgroundNetworkManager = extractManagedNetworkManager(mc.currentScreen)
            ?: mc.thePlayer?.sendQueue?.networkManager
            ?: runtime.backgroundNetworkManager
        runtime.disconnectedReason = null
        return runtime.hasRestorableState
    }

    fun syncActiveRuntime(tabId: String?) {
        if (tabId == null) {
            return
        }

        val runtime = runtimes[tabId] ?: return
        if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) {
            runtimes.remove(tabId)
            return
        }

        runtime.session = mc.session
        runtime.world = mc.theWorld
        runtime.player = mc.thePlayer
        runtime.playerController = mc.playerController
        runtime.serverData = mc.currentServerData
        runtime.renderViewEntity = mc.renderViewEntity ?: mc.thePlayer
        runtime.currentScreen = sanitizeScreen(mc.currentScreen)
        runtime.backgroundNetworkManager = extractManagedNetworkManager(mc.currentScreen)
            ?: mc.thePlayer?.sendQueue?.networkManager
            ?: runtime.backgroundNetworkManager
        runtime.disconnectedReason = null
        activeRuntimeTabId = tabId
    }

    fun enqueueIncomingPacket(handler: INetHandler?, packet: Packet<*>): Boolean {
        val runtime = findBackgroundRuntime(handler) ?: return false

        @Suppress("UNCHECKED_CAST")
        val rawPacket = packet as Packet<INetHandler>
        runtime.enqueueTask(Runnable {
            rawPacket.processPacket(handler)
        })
        return true
    }

    fun enqueueScheduledTask(handler: INetHandler?, task: Runnable): Boolean {
        val runtime = findBackgroundRuntime(handler) ?: return false
        runtime.enqueueTask(task)
        return true
    }

    fun activateRuntime(tabId: String): Boolean {
        val runtime = runtimes[tabId] ?: return false
        if (!runtime.hasRestorableState) {
            runtimes.remove(tabId)
            return false
        }

        mc.session = runtime.session
        mc.theWorld = runtime.world
        mc.thePlayer = runtime.player
        mc.playerController = runtime.playerController
        mc.renderViewEntity = runtime.renderViewEntity ?: runtime.player
        mc.objectMouseOver = null
        mc.pointedEntity = null
        mc.setServerData(runtime.serverData)
        activeRuntimeTabId = tabId
        SessionRuntimeScope.resetSharedActionStateForCurrentPlayer()
        runtime.drainQueuedTasks()

        runCatching {
            mc.renderGlobal.setWorldAndLoadRenderers(runtime.world)
        }

        sanitizeScreen(runtime.currentScreen)?.let { screen ->
            runtime.currentScreen = screen
            val scaledResolution = net.minecraft.client.gui.ScaledResolution(mc)
            screen.setWorldAndResolution(mc, scaledResolution.scaledWidth, scaledResolution.scaledHeight)
            mc.currentScreen = screen
        } ?: run {
            runtime.currentScreen = null
            mc.displayGuiScreen(null)
        }
        return true
    }

    fun clearMinecraftRuntime(screen: GuiScreen? = null) {
        activeRuntimeTabId = null
        mc.theWorld = null
        mc.thePlayer = null
        mc.playerController = null
        mc.renderViewEntity = null
        mc.objectMouseOver = null
        mc.pointedEntity = null
        mc.setServerData(null)
        SessionRuntimeScope.resetSharedActionStateForCurrentPlayer()

        runCatching {
            mc.renderGlobal.setWorldAndLoadRenderers(null)
        }

        mc.displayGuiScreen(screen ?: GuiMainMenu())
    }

    fun removeRuntime(tabId: String, disconnect: Boolean = true) {
        val runtime = runtimes.remove(tabId) ?: return
        deactivateRuntime(tabId)
        runtime.clearQueuedTasks()
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
        runtime.serverData = serverData
        runtime.currentScreen = sanitizeScreen(screen)
        runtime.disconnectedReason = null

        return SessionRuntimeScope.runDetached(runtime, clearScreen = false) {
            runCatching {
                val serverAddress = net.minecraft.client.multiplayer.ServerAddress.fromString(serverData.serverIP)
                mc.theWorld = null
                mc.thePlayer = null
                mc.playerController = null
                mc.renderViewEntity = null
                mc.objectMouseOver = null
                mc.pointedEntity = null
                mc.setServerData(serverData)

                val inetAddress = java.net.InetAddress.getByName(serverAddress.ip)
                val networkManager = NetworkManager.createNetworkManagerAndConnect(
                    inetAddress,
                    serverAddress.port,
                    mc.gameSettings.isUsingNativeTransport
                )

                runtime.backgroundNetworkManager = networkManager
                networkManager.netHandler = net.minecraft.client.network.NetHandlerLoginClient(
                    networkManager,
                    mc,
                    screen ?: net.asd.union.ui.client.gui.GuiMainMenu()
                )
                networkManager.sendPacket(
                    net.minecraft.network.handshake.client.C00Handshake(
                        47,
                        serverAddress.ip,
                        serverAddress.port,
                        net.minecraft.network.EnumConnectionState.LOGIN,
                        true
                    )
                )
                networkManager.sendPacket(
                    net.minecraft.network.login.client.C00PacketLoginStart(mc.session.profile)
                )
                true
            }.getOrElse {
                runtime.disconnectedReason = it.message ?: "Failed to connect"
                false
            }
        }
    }

    fun disconnectRuntimeToScreen(tabId: String, reason: String, screen: GuiScreen?) {
        val runtime = runtimes[tabId] ?: return
        runtime.currentScreen = screen
        runtime.disconnect(reason)
        runtime.world = null
        runtime.player = null
        runtime.playerController = null
        runtime.renderViewEntity = null
        runtime.backgroundNetworkManager = null
    }

    fun tickBackgroundRuntimes(activeTabId: String?) {
        val now = System.nanoTime()
        backgroundTickCarryNanos += (now - lastBackgroundTickNanos).coerceAtLeast(0L)
        lastBackgroundTickNanos = now

        var steps = (backgroundTickCarryNanos / BACKGROUND_TICK_NANOS).toInt()
        if (steps <= 0) {
            steps = 1
        } else {
            backgroundTickCarryNanos %= BACKGROUND_TICK_NANOS
        }
        steps = steps.coerceAtMost(MAX_CATCH_UP_STEPS)

        val runtimesToTick = runtimes.values.filter { it.tabId != activeTabId }.toList()

        runtimesToTick.forEach { runtime ->
            if (!runtime.hasRestorableState) {
                runtimes.remove(runtime.tabId)
                return@forEach
            }

            repeat(steps) {
                SessionRuntimeScope.runDetached(runtime) {
                    runtime.drainQueuedTasks()
                    val networkManager = runtime.networkManager

                    if (networkManager != null) {
                        if (networkManager.isChannelOpen) {
                            networkManager.processReceivedPackets()
                        } else {
                            networkManager.checkDisconnected()
                        }
                    }

                    runtime.drainQueuedTasks()
                    ClientTabManager.applyMirroredLookState(runtime.tabId)
                    runtime.currentScreen?.updateScreen()

                    runCatching {
                        runtime.playerController?.updateController()
                    }

                    val worldTicked = runCatching {
                        runtime.world?.updateEntities()
                        runtime.world != null
                    }.getOrDefault(false)

                    if (!worldTicked) {
                        runCatching {
                            runtime.player?.onUpdate()
                        }
                    }

                    runtime.drainQueuedTasks()
                    ClientTabManager.applyMirroredIngameActions(runtime.tabId)
                }

                if (!runtime.hasRestorableState) {
                    return@repeat
                }
            }

            if (runtime.networkManager?.isChannelOpen == false && runtime.disconnectedReason == null) {
                runtime.disconnectedReason = "Disconnected"
            }

            if (!runtime.hasRestorableState) {
                runtimes.remove(runtime.tabId)
            }
        }
    }

    fun markCurrentRuntimeDisconnected(reason: IChatComponent?) {
        SessionRuntimeScope.currentRuntime()?.let { runtime ->
            runtime.disconnectedReason = reason?.unformattedText ?: "Disconnected"
        }
    }

    private fun findBackgroundRuntime(handler: INetHandler?): LiveTabRuntime? {
        return runtimes.values.firstOrNull { runtime ->
            runtime.tabId != activeRuntimeTabId && runtime.currentHandler === handler
        }
    }

    private fun extractManagedNetworkManager(screen: GuiScreen?): NetworkManager? {
        val connecting = screen as? net.minecraft.client.multiplayer.GuiConnecting ?: return null
        return (connecting as? MixinGuiConnectingAccessor)?.networkManager
    }
}

class LiveTabRuntime(val tabId: String) {
    var session: Session = MinecraftInstance.mc.session
    var world: WorldClient? = null
    var player: EntityPlayerSP? = null
    var playerController: PlayerControllerMP? = null
    var serverData: ServerData? = null
    var renderViewEntity: Entity? = null
    var currentScreen: GuiScreen? = null
    var backgroundNetworkManager: NetworkManager? = null
    var disconnectedReason: String? = null
    private val queuedTasks = ConcurrentLinkedQueue<Runnable>()

    val netHandler: NetHandlerPlayClient?
        get() = player?.sendQueue

    val currentHandler: INetHandler?
        get() = netHandler ?: backgroundNetworkManager?.netHandler

    val networkManager: NetworkManager?
        get() = netHandler?.networkManager ?: backgroundNetworkManager

    val hasWorldState: Boolean
        get() = world != null && player != null && playerController != null

    val hasRestorableState: Boolean
        get() = disconnectedReason == null &&
            (hasWorldState || currentScreen != null || networkManager != null) &&
            networkManager?.isChannelOpen != false

    fun enqueueTask(task: Runnable) {
        queuedTasks += task
    }

    fun drainQueuedTasks() {
        while (true) {
            val task = queuedTasks.poll() ?: return
            runCatching {
                task.run()
            }.onFailure { throwable ->
                ClientUtils.LOGGER.error("Failed to process queued tab task for $tabId", throwable)
            }
        }
    }

    fun clearQueuedTasks() {
        while (queuedTasks.poll() != null) {
            // Keep polling until the queue is empty.
        }
    }

    fun disconnect(reason: String) {
        disconnectedReason = reason
        clearQueuedTasks()

        networkManager?.let { manager ->
            runCatching {
                manager.closeChannel(ChatComponentText(reason))
            }
            runCatching {
                manager.checkDisconnected()
            }
        }

        backgroundNetworkManager = null
    }
}
