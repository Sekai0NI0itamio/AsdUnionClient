/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.*
import net.asd.union.event.Render2DEvent
import net.asd.union.event.Render3DEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.attack.EntityUtils.colorFromDisplayName
import net.asd.union.utils.attack.EntityUtils.isLookingOnEntities
import net.asd.union.utils.attack.EntityUtils.isSelected
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.EntityCache
import net.asd.union.utils.extensions.*
import net.asd.union.utils.render.ColorSettingsInteger
import net.asd.union.utils.render.RenderUtils.draw2D
import net.asd.union.utils.render.RenderUtils.drawEntityBox
import net.asd.union.utils.render.WorldToScreen
import net.asd.union.utils.render.shader.shaders.GlowShader
import net.asd.union.utils.rotation.RotationUtils.isEntityHeightVisible
import net.minecraft.client.renderer.GlStateManager.enableTexture2D
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11.*
import org.lwjgl.util.vector.Vector3f
import java.awt.Color
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object ESP : Module("ESP", Category.VISUAL, hideModule = false) {

    val mode by choices(
        "Mode",
        arrayOf("Box", "OtherBox", "WireFrame", "2D", "Real2D", "Gaussian", "Outline", "Glow"), "Box"
    )

    val outlineWidth by float("Outline-Width", 3f, 0.5f..5f) { mode == "Outline" }

    val wireframeWidth by float("WireFrame-Width", 2f, 0.5f..5f) { mode == "WireFrame" }

    private val glowRenderScale by float("Glow-Renderscale", 1f, 0.5f..2f) { mode == "Glow" }
    private val glowRadius by int("Glow-Radius", 4, 1..5) { mode == "Glow" }
    private val glowFade by int("Glow-Fade", 10, 0..30) { mode == "Glow" }
    private val glowTargetAlpha by float("Glow-Target-Alpha", 0f, 0f..1f) { mode == "Glow" }

    private val espColor = ColorSettingsInteger(this, "ESP").with(255, 255, 255)

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 80, 1..150) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private var cachedRenderEntries: List<EspRenderEntry> = emptyList()
    private var lastCacheTick = -1
    private var lastCacheKey = ""
    private var frustum: Frustum? = null

    private val glowBuckets = HashMap<Int, MutableList<EntityLivingBase>>()
    private val glowColors = HashMap<Int, Color>()
    private val activeGlowKeys = ArrayList<Int>()

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val colorTeam by boolean("TeamColor", false)
    private val bot by boolean("Bots", true)

    var renderNameTags = true

    private fun getFrustum(): Frustum? {
        frustum?.let { return it }

        return try {
            Frustum().also { frustum = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun clearGlowBuckets() {
        for (key in activeGlowKeys) {
            glowBuckets[key]?.clear()
        }

        activeGlowKeys.clear()
    }

    private fun project(
        vec: Vector3f,
        mvMatrix: org.lwjgl.util.vector.Matrix4f,
        projectionMatrix: org.lwjgl.util.vector.Matrix4f,
        displayWidth: Int,
        displayHeight: Int,
        minX: FloatArray,
        minY: FloatArray,
        maxX: FloatArray,
        maxY: FloatArray,
        x: Double,
        y: Double,
        z: Double,
    ) {
        vec.set(x.toFloat(), y.toFloat(), z.toFloat())

        val screenPos = WorldToScreen.worldToScreen(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight)
            ?: return

        minX[0] = min(screenPos.x, minX[0])
        minY[0] = min(screenPos.y, minY[0])
        maxX[0] = max(screenPos.x, maxX[0])
        maxY[0] = max(screenPos.y, maxY[0])
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: run {
            cachedRenderEntries = emptyList()
            clearGlowBuckets()
            return@handler
        }

        if (mc.theWorld == null) {
            cachedRenderEntries = emptyList()
            clearGlowBuckets()
            return@handler
        }

        val cacheKey = buildString {
            append("range=").append(maxRenderDistanceSq).append(',')
            append("onLook=").append(onLook).append(',')
            append("maxAngle=").append(maxAngleDifference).append(',')
            append("thruBlocks=").append(thruBlocks).append(',')
            append("team=").append(colorTeam).append(',')
            append("bots=").append(bot).append(',')
            append("mode=").append(mode)
        }

        val tick = player.ticksExisted
        if (tick == lastCacheTick && cacheKey == lastCacheKey) {
            return@handler
        }

        lastCacheTick = tick
        lastCacheKey = cacheKey

        val entities = EntityCache.getEntitiesWithValidityCheck(
            range = maxRenderDistance.toDouble(),
            onLook = onLook,
            maxAngleDifference = maxAngleDifference.toDouble(),
            thruBlocks = thruBlocks,
        )

        if (entities.isEmpty()) {
            cachedRenderEntries = emptyList()
            clearGlowBuckets()
            return@handler
        }

        val entries = ArrayList<EspRenderEntry>(entities.size)
        for (entity in entities) {
            entries += EspRenderEntry(entity, getColor(entity), entity.hitBox, entity.currPos)
        }

        cachedRenderEntries = entries
        clearGlowBuckets()

        if (mode == "Glow") {
            for (entry in entries) {
                val key = entry.color.rgb
                val list = glowBuckets.getOrPut(key) { mutableListOf() }

                if (list.isEmpty()) {
                    activeGlowKeys += key
                    glowColors[key] = entry.color
                }

                list += entry.entity
            }
        }
    }

    val onRender3D = handler<Render3DEvent> {
        if (mc.theWorld == null || mc.thePlayer == null)
            return@handler

        val entries = cachedRenderEntries
        if (entries.isEmpty())
            return@handler

        when (mode) {
            "Real2D" -> {
                val mvMatrix = WorldToScreen.getMatrix(GL_MODELVIEW_MATRIX)
                val projectionMatrix = WorldToScreen.getMatrix(GL_PROJECTION_MATRIX)

                glPushAttrib(GL_ENABLE_BIT)
                glEnable(GL_BLEND)
                glDisable(GL_TEXTURE_2D)
                glDisable(GL_DEPTH_TEST)
                glMatrixMode(GL_PROJECTION)
                glPushMatrix()
                glLoadIdentity()
                glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
                glMatrixMode(GL_MODELVIEW)
                glPushMatrix()
                glLoadIdentity()
                glDisable(GL_DEPTH_TEST)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                enableTexture2D()
                glDepthMask(true)
                glLineWidth(1f)

                val renderManager = mc.renderManager
                val frustumLocal = getFrustum()
                frustumLocal?.setPosition(renderManager.renderPosX, renderManager.renderPosY, renderManager.renderPosZ)
                val vec = Vector3f()
                val displayWidth = mc.displayWidth
                val displayHeight = mc.displayHeight

                for (entry in entries) {
                    if (frustumLocal != null && !frustumLocal.isBoundingBoxInFrustum(entry.hitBox)) {
                        continue
                    }

                    val pos = entry.entity.interpolatedPosition(entry.entity.lastTickPos) - renderManager.renderPos
                    val bb = entry.hitBox.offset(-entry.currPos + pos)

                    val minX = floatArrayOf(Float.MAX_VALUE)
                    val minY = floatArrayOf(Float.MAX_VALUE)
                    val maxX = floatArrayOf(-1f)
                    val maxY = floatArrayOf(-1f)

                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.minX, bb.minY, bb.minZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.minX, bb.maxY, bb.minZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.maxX, bb.maxY, bb.minZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.maxX, bb.minY, bb.minZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.minX, bb.minY, bb.maxZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.minX, bb.maxY, bb.maxZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.maxX, bb.maxY, bb.maxZ)
                    project(vec, mvMatrix, projectionMatrix, displayWidth, displayHeight, minX, minY, maxX, maxY, bb.maxX, bb.minY, bb.maxZ)

                    if (minX[0] > 0f || minY[0] > 0f || maxX[0] <= displayWidth || maxY[0] <= displayWidth) {
                        val color = entry.color
                        glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, 1f)
                        glBegin(GL_LINE_LOOP)
                        glVertex2f(minX[0], minY[0])
                        glVertex2f(minX[0], maxY[0])
                        glVertex2f(maxX[0], maxY[0])
                        glVertex2f(maxX[0], minY[0])
                        glEnd()
                    }
                }

                glColor4f(1f, 1f, 1f, 1f)
                glEnable(GL_DEPTH_TEST)
                glMatrixMode(GL_PROJECTION)
                glPopMatrix()
                glMatrixMode(GL_MODELVIEW)
                glPopMatrix()
                glPopAttrib()
            }

            "2D" -> {
                val renderManager = mc.renderManager
                for (entry in entries) {
                    val pos = entry.entity.interpolatedPosition(entry.entity.lastTickPos) - renderManager.renderPos
                    draw2D(entry.entity, pos.xCoord, pos.yCoord, pos.zCoord, entry.color.rgb, Color.BLACK.rgb)
                }
            }

            "Box", "OtherBox" -> {
                val outline = mode != "OtherBox"
                for (entry in entries) {
                    drawEntityBox(entry.entity, entry.color, outline)
                }
            }
        }
    }

    val onRender2D = handler<Render2DEvent> { event ->
        if (mc.theWorld == null || mode != "Glow" || activeGlowKeys.isEmpty())
            return@handler

        renderNameTags = false

        try {
            for (key in activeGlowKeys) {
                val list = glowBuckets[key] ?: continue
                if (list.isEmpty()) {
                    continue
                }

                GlowShader.startDraw(event.partialTicks, glowRenderScale)

                for (entity in list) {
                    mc.renderManager.renderEntitySimple(entity, event.partialTicks)
                }

                GlowShader.stopDraw(glowColors[key] ?: espColor.color(), glowRadius, glowFade, glowTargetAlpha)
            }
        } catch (ex: Exception) {
            LOGGER.error("An error occurred while rendering all entities for shader esp", ex)
        } finally {
            renderNameTags = true
        }
    }

    override val tag
        get() = mode

    fun getColor(entity: Entity? = null): Color {
        if (entity != null && entity is EntityLivingBase) {
            if (entity.hurtTime > 0)
                return Color.RED

            if (entity is EntityPlayer && entity.isClientFriend())
                return Color.BLUE

            if (colorTeam) {
                entity.colorFromDisplayName()?.let {
                    return it
                }
            }
        }

        return espColor.color()
    }

    fun shouldRender(entity: EntityLivingBase): Boolean {
        val player = mc.thePlayer ?: return false

        return (player.getDistanceSqToEntity(entity) <= maxRenderDistanceSq
                && (thruBlocks || isEntityHeightVisible(entity))
                && (!onLook || isLookingOnEntities(entity, maxAngleDifference.toDouble()))
                && isSelected(entity, false)
                && (bot || true))
    }

    private data class EspRenderEntry(
        val entity: EntityLivingBase,
        val color: Color,
        val hitBox: AxisAlignedBB,
        val currPos: Vec3,
    )
}
