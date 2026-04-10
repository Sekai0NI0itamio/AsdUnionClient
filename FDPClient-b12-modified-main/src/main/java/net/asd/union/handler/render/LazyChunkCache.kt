/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.render

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
                clear()
            }
        }

    private val cache = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    var skippedCount = 0
        private set

    private fun key(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xffffffffL)
    }

    fun contains(chunkX: Int, chunkZ: Int): Boolean {
        return cache.contains(key(chunkX, chunkZ))
    }

    fun add(chunkX: Int, chunkZ: Int) {
        cache.add(key(chunkX, chunkZ))
    }

    fun remove(chunkX: Int, chunkZ: Int) {
        cache.remove(key(chunkX, chunkZ))
    }

    fun clear() {
        val clearedChunks = cache.size
        cache.clear()
        skippedCount = 0

        if (clearedChunks > 0) {
            ClientUtils.LOGGER.info("[LazyChunkCache] Cleared$clearedChunks cached chunks")
        }
    }

    fun recordSkip() {
        skippedCount += 1
    }

    fun getCacheSize(): Int = cache.size
}
