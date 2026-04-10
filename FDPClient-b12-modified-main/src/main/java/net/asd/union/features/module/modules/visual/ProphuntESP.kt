/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual


import net.asd.union.config.*
import net.asd.union.event.Render2DEvent
import net.asd.union.event.Render3DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.attack.EntityUtils.isLookingOnEntities
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.EntityLookup
import net.asd.union.utils.render.RenderUtils.drawBlockBox
import net.asd.union.utils.render.RenderUtils.drawEntityBox
import net.asd.union.utils.render.shader.shaders.GlowShader
import net.asd.union.utils.rotation.RotationUtils.isEntityHeightVisible
import net.minecraft.entity.item.EntityFallingBlock
import net.minecraft.util.BlockPos
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

object ProphuntESP : Module("ProphuntESP", Category.VISUAL, gameDetecting = false) {
    private val mode by choices("Mode", arrayOf("Box", "OtherBox", "Glow"), "OtherBox")
    private val glowRenderScale by float("Glow-Renderscale", 1f, 0.5f..2f) { mode == "Glow" }
    private val glowRadius by int("Glow-Radius", 4, 1..5) { mode == "Glow" }
    private val glowFade by int("Glow-Fade", 10, 0..30) { mode == "Glow" }
    private val glowTargetAlpha by float("Glow-Target-Alpha", 0f, 0f..1f) { mode == "Glow" }

    private val color by color("Color", Color(0, 90, 255))

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 50, 1..200) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

    private val blocks = ConcurrentHashMap<BlockPos, Long>()

    private val entities by EntityLookup<EntityFallingBlock>()
        .filter { !onLook || isLookingOnEntities(it, maxAngleDifference.toDouble()) }
        .filter { thruBlocks || isEntityHeightVisible(it) }
        .filter { mc.thePlayer.getDistanceSqToEntity(it) <= maxRenderDistanceSq }

    fun recordBlock(blockPos: BlockPos) {
        blocks[blockPos] = System.currentTimeMillis()
    }

    override fun onDisable() {
        blocks.clear()
    }

    val handleFallingBlocks = handler<Render3DEvent> {
        if (mode != "Box" && mode != "OtherBox") return@handler

        for (entity in entities) {
            drawEntityBox(entity, color, mode == "Box")
        }
    }

    val handleUpdateBlocks = handler<Render3DEvent> {
        val now = System.currentTimeMillis()

        with(blocks.entries.iterator()) {
            while (hasNext()) {
                val (pos, time) = next()

                if (now - time > 2000L) {
                    remove()
                    continue
                }

                drawBlockBox(pos, color, mode == "Box")
            }
        }
    }

    val onRender2D = handler<Render2DEvent> { event ->
        if (mc.theWorld == null || mode != "Glow") return@handler

        GlowShader.startDraw(event.partialTicks, glowRenderScale)

        for (entity in entities) {
            try {
                mc.renderManager.renderEntityStatic(entity, mc.timer.renderPartialTicks, true)
            } catch (ex: Exception) {
                LOGGER.error("An error occurred while rendering all entities for shader esp", ex)
            }
        }

        GlowShader.stopDraw(color, glowRadius, glowFade, glowTargetAlpha)
    }
}