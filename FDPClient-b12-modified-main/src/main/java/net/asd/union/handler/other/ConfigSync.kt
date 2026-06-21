/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.other

import net.asd.union.event.GameTickEvent
import net.asd.union.event.Listenable
import net.asd.union.event.WorldEvent
import net.asd.union.event.handler
import net.asd.union.file.FileConfig
import net.asd.union.file.FileManager
import net.asd.union.file.FileManager.clickGuiConfig
import net.asd.union.file.FileManager.hudConfig
import net.asd.union.file.FileManager.modulesConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.handler.sessiontabs.SessionRuntimeScope
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.gui.GuiDownloadTerrain

object ConfigSync : Listenable, MinecraftInstance {
    private const val WORLD_CHANGE_SAVE_DELAY_MS = 2_000L
    private const val SESSION_UPDATE_SAVE_DELAY_MS = 750L
    private const val SAVE_GAP_MS = 250L

    private var lastWorldPresent = false
    private val pendingConfigs = linkedSetOf<FileConfig>()
    private var nextFlushAtMs = 0L

    fun requestDeferredSave(config: FileConfig, delayMs: Long = WORLD_CHANGE_SAVE_DELAY_MS) {
        pendingConfigs += config
        nextFlushAtMs = maxOf(nextFlushAtMs, System.currentTimeMillis() + delayMs.coerceAtLeast(0L))
    }

    private val onWorld = handler<WorldEvent> { event ->
        val hasWorld = event.worldClient != null

        if (hasWorld != lastWorldPresent) {
            requestDeferredSave(modulesConfig)
            requestDeferredSave(valuesConfig)
            requestDeferredSave(clickGuiConfig)
            requestDeferredSave(hudConfig)
        }

        lastWorldPresent = hasWorld
    }

    val onTick = handler<GameTickEvent>(always = true) {
        if (pendingConfigs.isEmpty()) {
            return@handler
        }

        if (SessionRuntimeScope.isDetachedContextActive() ||
            Thread.currentThread() is net.asd.union.handler.sessiontabs.TabSimulationThread) {
            return@handler
        }

        if (!ClientTabManager.canPersistToMainStorage()) {
            return@handler
        }

        if (System.currentTimeMillis() < nextFlushAtMs) {
            return@handler
        }

        if (mc.currentScreen is GuiDownloadTerrain) {
            return@handler
        }

        val nextConfig = pendingConfigs.firstOrNull() ?: return@handler
        pendingConfigs.remove(nextConfig)

        runCatching {
            FileManager.saveConfig(nextConfig, false)
        }.onFailure { e ->
            ClientUtils.LOGGER.warn("[ConfigSync] Failed to flush ${nextConfig.file.name}: ${e.message}")
        }

        nextFlushAtMs = if (pendingConfigs.isEmpty()) {
            0L
        } else {
            System.currentTimeMillis() + SAVE_GAP_MS
        }
    }

    fun requestSessionStorageSave() {
        requestDeferredSave(valuesConfig, SESSION_UPDATE_SAVE_DELAY_MS)
    }
}
