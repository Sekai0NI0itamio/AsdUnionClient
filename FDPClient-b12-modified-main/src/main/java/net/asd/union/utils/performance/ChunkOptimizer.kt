package net.asd.union.utils.performance

import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.renderer.ViewFrustum
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher
import net.minecraft.client.renderer.chunk.RenderChunk
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import kotlin.math.abs

object ChunkOptimizer : MinecraftInstance {
    var enabled = true

    @Volatile
    var skipNextLoadRenderers = false

    var rotationThreshold = 1.0
    var asyncNearChunks = true
    var syncCompileDistance = 16.0
    var precompile360 = true
    var precompilePerTick = 4
    var precompileRadius = 6

    private var lastBfsYaw = Float.NaN
    private var lastBfsPitch = Float.NaN
    private var precompileScanIndex = 0
    private var lastPrecompileChunkX = Int.MIN_VALUE
    private var lastPrecompileChunkZ = Int.MIN_VALUE

    fun shouldRefreshBFS(yaw: Float, pitch: Float): Boolean {
        if (!enabled) {
            return true
        }

        if (lastBfsYaw.isNaN()) {
            lastBfsYaw = yaw
            lastBfsPitch = pitch
            return true
        }

        val yawDiff = angleDiff(yaw, lastBfsYaw)
        val pitchDiff = abs(pitch - lastBfsPitch).toDouble()
        if (yawDiff < rotationThreshold && pitchDiff < rotationThreshold) {
            return false
        }

        lastBfsYaw = yaw
        lastBfsPitch = pitch
        return true
    }

    fun shouldCompileAsync(renderChunk: RenderChunk): Boolean {
        if (!enabled || !asyncNearChunks) {
            return false
        }

        val player = mc.thePlayer ?: return false
        val pos = renderChunk.getPosition()

        val px = MathHelper.floor_double(player.posX) shr 4
        val py = MathHelper.floor_double(player.posY) shr 4
        val pz = MathHelper.floor_double(player.posZ) shr 4
        val cx = pos.x shr 4
        val cy = pos.y shr 4
        val cz = pos.z shr 4

        return px != cx || py != cy || pz != cz
    }

    fun precompileNearbyChunks(viewFrustum: ViewFrustum?, dispatcher: ChunkRenderDispatcher?) {
        if (!enabled || !precompile360 || viewFrustum == null || dispatcher == null) {
            return
        }

        val player = mc.thePlayer ?: return
        val chunkX = MathHelper.floor_double(player.posX) shr 4
        val chunkZ = MathHelper.floor_double(player.posZ) shr 4

        if (chunkX != lastPrecompileChunkX || chunkZ != lastPrecompileChunkZ) {
            lastPrecompileChunkX = chunkX
            lastPrecompileChunkZ = chunkZ
            precompileScanIndex = 0
        }

        val diameter = precompileRadius * 2 + 1
        val totalChunkColumns = diameter * diameter
        val sectionsPerColumn = 16
        var scheduled = 0
        var attempts = 0

        while (scheduled < precompilePerTick && attempts < totalChunkColumns) {
            val index = precompileScanIndex % totalChunkColumns
            val (offX, offZ) = spiralOffset(index, diameter)
            val scanChunkX = chunkX + offX
            val scanChunkZ = chunkZ + offZ

            for (sectionY in 0 until sectionsPerColumn) {
                if (scheduled >= precompilePerTick) {
                    break
                }

                val blockPos = BlockPos(scanChunkX shl 4, sectionY shl 4, scanChunkZ shl 4)
                val renderChunk = runCatching {
                    viewFrustum.getRenderChunk(blockPos)
                }.getOrNull() ?: continue

                if (!renderChunk.isNeedsUpdate()) {
                    continue
                }

                val submitted = dispatcher.updateChunkLater(renderChunk)
                if (!submitted) {
                    return
                }

                renderChunk.setNeedsUpdate(false)
                scheduled++
            }

            precompileScanIndex++
            attempts++
        }

        if (precompileScanIndex >= totalChunkColumns) {
            precompileScanIndex = 0
        }
    }

    fun reset() {
        lastBfsYaw = Float.NaN
        lastBfsPitch = Float.NaN
        precompileScanIndex = 0
        lastPrecompileChunkX = Int.MIN_VALUE
        lastPrecompileChunkZ = Int.MIN_VALUE
    }

    private fun angleDiff(a: Float, b: Float): Double {
        var diff = (a - b).toDouble() % 360.0
        if (diff < -180.0) diff += 360.0
        if (diff > 180.0) diff -= 360.0
        return abs(diff)
    }

    private fun spiralOffset(index: Int, diameter: Int): Pair<Int, Int> {
        if (index == 0) {
            return 0 to 0
        }

        var x = 0
        var z = 0
        var dx = 0
        var dz = -1
        val max = diameter * diameter

        for (i in 0 until max) {
            if (i == index) {
                return x to z
            }

            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                val temp = dx
                dx = -dz
                dz = temp
            }

            x += dx
            z += dz
        }

        return 0 to 0
    }
}
