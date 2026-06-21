/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.render

import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.handler.sessiontabs.SessionRuntimeScope
import net.asd.union.handler.sessiontabs.TabSimulationThread
import net.asd.union.utils.client.ClientUtils
import java.util.concurrent.ConcurrentHashMap

object LazyChunkCache {
    var enabled = false
        set(value) {
            if (field == value) {
                return
            }

            field = value
            ClientUtils.LOGGER.info("[LazyChunkCache]${if (value) "Enabled" else "Disabled"}")

            if (!value) {
                clearAllContexts()
            }
        }

    private data class CacheState(
        val chunks: MutableSet<Long> = ConcurrentHashMap.newKeySet(),
        @Volatile var skippedCount: Int = 0
    )

    private val cacheByContext = ConcurrentHashMap<String, CacheState>()

    @Volatile
    var skippedCount = 0
        private set

    private fun currentContextKey(): String {
        return SessionRuntimeScope.currentRuntime()?.tabId
            ?: (Thread.currentThread() as? TabSimulationThread)?.runtime?.tabId
            ?: ClientTabManager.currentTabId()
            ?: "__global__"
    }

    private fun currentCacheState(): CacheState {
        return cacheByContext.computeIfAbsent(currentContextKey()) { CacheState() }
    }

    private fun refreshVisibleSkippedCount() {
        skippedCount = currentCacheState().skippedCount
    }

    private fun totalCachedChunks(): Int {
        return cacheByContext.values.sumOf { it.chunks.size }
    }

    private fun key(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xffffffffL)
    }

    fun contains(chunkX: Int, chunkZ: Int): Boolean {
        return currentCacheState().chunks.contains(key(chunkX, chunkZ))
    }

    fun add(chunkX: Int, chunkZ: Int) {
        currentCacheState().chunks.add(key(chunkX, chunkZ))
    }

    fun remove(chunkX: Int, chunkZ: Int) {
        currentCacheState().chunks.remove(key(chunkX, chunkZ))
    }

    fun clear() {
        val contextKey = currentContextKey()
        val clearedChunks = cacheByContext.remove(contextKey)?.chunks?.size ?: 0
        refreshVisibleSkippedCount()

        if (clearedChunks > 0) {
            ClientUtils.LOGGER.info("[LazyChunkCache][$contextKey] Cleared $clearedChunks cached chunks")
        }
    }

    fun recordSkip() {
        val state = currentCacheState()
        state.skippedCount += 1
        skippedCount = state.skippedCount
    }

    fun getCacheSize(): Int = currentCacheState().chunks.size

    fun clearContext(contextKey: String) {
        val clearedChunks = cacheByContext.remove(contextKey)?.chunks?.size ?: 0

        if (currentContextKey() == contextKey) {
            refreshVisibleSkippedCount()
        } else if (cacheByContext.isEmpty()) {
            skippedCount = 0
        }

        if (clearedChunks > 0) {
            ClientUtils.LOGGER.info("[LazyChunkCache][$contextKey] Cleared $clearedChunks cached chunks")
        }
    }

    fun clearAllContexts() {
        val clearedChunks = totalCachedChunks()
        cacheByContext.clear()
        skippedCount = 0

        if (clearedChunks > 0) {
            ClientUtils.LOGGER.info("[LazyChunkCache] Cleared $clearedChunks cached chunks across all contexts")
        }
    }
}
