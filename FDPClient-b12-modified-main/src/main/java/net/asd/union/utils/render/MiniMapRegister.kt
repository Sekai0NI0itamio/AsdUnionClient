/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.render

import net.asd.union.event.Listenable
import net.asd.union.event.Render2DEvent
import net.asd.union.event.handler
import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.handler.sessiontabs.SessionRuntimeScope
import net.asd.union.handler.sessiontabs.TabSimulationThread
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.BlockPos
import net.minecraft.world.chunk.Chunk
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object MiniMapRegister : MinecraftInstance, Listenable {

    private const val MAX_CHUNK_UPDATES_PER_FRAME = 48
    private const val MAX_CHUNK_DELETIONS_PER_FRAME = 128

    private data class ContextState(
        val chunkTextureMap: MutableMap<ChunkLocation, MiniMapTexture> = HashMap(256),
        val queuedChunkUpdates: MutableSet<Chunk> = LinkedHashSet(256),
        val queuedChunkDeletions: MutableSet<ChunkLocation> = LinkedHashSet(256),
        var deleteAllChunks: Boolean = false
    )

    private val states = HashMap<String, ContextState>()

    private val lock = ReentrantReadWriteLock()

    private fun currentContextKey(): String {
        return SessionRuntimeScope.currentRuntime()?.tabId
            ?: (Thread.currentThread() as? TabSimulationThread)?.runtime?.tabId
            ?: ClientTabManager.currentTabId()
            ?: "__global__"
    }

    private fun currentState(): ContextState {
        return states.getOrPut(currentContextKey(), ::ContextState)
    }

    private fun disposeState(state: ContextState) {
        state.chunkTextureMap.values.forEach(MiniMapTexture::delete)
        state.chunkTextureMap.clear()
        state.queuedChunkUpdates.clear()
        state.queuedChunkDeletions.clear()
        state.deleteAllChunks = false
    }

    fun updateChunk(chunk: Chunk) {
        lock.write {
            currentState().queuedChunkUpdates += chunk
        }
    }

    fun getChunkTextureAt(x: Int, z: Int) = lock.read {
        states[currentContextKey()]?.chunkTextureMap?.get(ChunkLocation(x, z))
    }

    val onRender2D = handler<Render2DEvent> {
        updateChunks()
    }

    private fun updateChunks() {
        lock.write {
            val state = states[currentContextKey()] ?: return

            if (state.deleteAllChunks) {
                state.queuedChunkDeletions.clear()
                state.queuedChunkUpdates.clear()

                state.chunkTextureMap.values.forEach(MiniMapTexture::delete)
                state.chunkTextureMap.clear()

                state.deleteAllChunks = false
            } else {
                repeat(MAX_CHUNK_DELETIONS_PER_FRAME) {
                    val iterator = state.queuedChunkDeletions.iterator()
                    if (!iterator.hasNext()) {
                        return@repeat
                    }

                    val location = iterator.next()
                    iterator.remove()
                    state.chunkTextureMap.remove(location)?.delete()
                }
            }

            repeat(MAX_CHUNK_UPDATES_PER_FRAME) {
                val iterator = state.queuedChunkUpdates.iterator()
                if (!iterator.hasNext()) {
                    return@repeat
                }

                val chunk = iterator.next()
                iterator.remove()
                state.chunkTextureMap.getOrPut(chunk.location, ::MiniMapTexture).updateChunkData(chunk)
            }
        }
    }

    fun getLoadedChunkCount() = lock.read { states[currentContextKey()]?.chunkTextureMap?.size ?: 0 }

    fun unloadChunk(x: Int, z: Int) {
        lock.write {
            currentState().queuedChunkDeletions += ChunkLocation(x, z)
        }
    }

    fun unloadAllChunks() = lock.write {
        currentState().deleteAllChunks = true
    }

    fun clearContext(tabId: String) {
        lock.write {
            states.remove(tabId)?.let(::disposeState)
        }
    }

    fun clearAllContexts() {
        lock.write {
            states.values.forEach(::disposeState)
            states.clear()
        }
    }

    class MiniMapTexture {
        val texture = DynamicTexture(16, 16)
        private var deleted = false

        fun updateChunkData(chunk: Chunk) {
            val rgbValues = texture.textureData

            val pos = BlockPos.MutableBlockPos()
            for (x in 0..15) {
                for (z in 0..15) {
                    val bp = pos.set(x, chunk.getHeightValue(x, z) - 1, z)
                    val blockState = chunk.getBlockState(bp)

                    rgbValues[rgbValues.size - 1 - (z shl 4 or x)] = blockState.block.getMapColor(blockState).colorValue or (0xFF shl 24)
                }
            }

            texture.updateDynamicTexture()
        }

        internal fun delete() {
            if (!deleted) {
                texture.deleteGlTexture()
                deleted = true
            }
        }

        protected fun finalize() {
            // We don't need to set deleted to true since the object is deleted after this method call
            if (!deleted)
                texture.deleteGlTexture()
        }
    }

    private val Chunk.location: ChunkLocation
        get() = ChunkLocation(xPosition, zPosition)

    data class ChunkLocation(val x: Int, val z: Int)

}
