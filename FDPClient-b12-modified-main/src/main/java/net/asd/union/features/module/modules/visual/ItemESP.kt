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
import net.asd.union.features.module.modules.player.InventoryCleaner
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.ItemCache
import net.asd.union.utils.extensions.*
import net.asd.union.utils.render.RenderUtils.disableGlCap
import net.asd.union.utils.render.RenderUtils.drawEntityBox
import net.asd.union.utils.render.RenderUtils.enableGlCap
import net.asd.union.utils.render.RenderUtils.resetCaps
import net.asd.union.utils.render.shader.shaders.GlowShader
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.pow

object ItemESP : Module("ItemESP", Category.VISUAL, hideModule = false) {
    private val mode by choices("Mode", arrayOf("Box", "OtherBox", "Glow"), "Box")

    private val itemText by boolean("ItemText", false)
    private val itemTextTag by choices("ItemTextTag", arrayOf("()", "x", "[]"), "()")

    private val glowRenderScale by float("Glow-Renderscale", 1f, 0.5f..2f) { mode == "Glow" }
    private val glowRadius by int("Glow-Radius", 4, 1..5) { mode == "Glow" }
    private val glowFade by int("Glow-Fade", 10, 0..30) { mode == "Glow" }
    private val glowTargetAlpha by float("Glow-Target-Alpha", 0f, 0f..1f) { mode == "Glow" }

    private val color by color("Color", Color.BLUE)

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 50, 1..100) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private val scale by float("Scale", 3F, 1F..5F) { itemText }
    private val itemCounts by boolean("ItemCounts", true) { itemText }
    private val font by font("Font", Fonts.font40) { itemText }
    private val fontShadow by boolean("Shadow", true) { itemText }

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private var cachedRenderEntries: List<ItemRenderEntry> = emptyList()
    private var lastCacheTick = -1
    private var lastCacheKey = ""
    private var frustum: Frustum? = null

    private val glowBuckets = HashMap<Int, MutableList<EntityItem>>()
    private val glowColors = HashMap<Int, Color>()
    private val activeGlowKeys = ArrayList<Int>()

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

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

        val highlightUseful = InventoryCleaner.handleEvents() && InventoryCleaner.highlightUseful
        val cacheKey = buildString {
            append("range=").append(maxRenderDistanceSq).append(',')
            append("onLook=").append(onLook).append(',')
            append("maxAngle=").append(maxAngleDifference).append(',')
            append("thruBlocks=").append(thruBlocks).append(',')
            append("itemText=").append(itemText).append(',')
            append("itemCounts=").append(itemCounts).append(',')
            append("itemTextTag=").append(itemTextTag).append(',')
            append("font=").append(font.hashCode()).append(',')
            append("color=").append(color.rgb).append(',')
            append("mode=").append(mode).append(',')
            append("highlight=").append(highlightUseful)
        }

        val tick = player.ticksExisted
        if (tick == lastCacheTick && cacheKey == lastCacheKey) {
            return@handler
        }

        lastCacheTick = tick
        lastCacheKey = cacheKey

        ItemCache.maxRenderDistanceSq = maxRenderDistanceSq
        val itemEntities = ItemCache.getItemsWithValidityCheck(
            range = maxRenderDistance.toDouble(),
            onLook = onLook,
            maxAngleDifference = maxAngleDifference.toDouble(),
            thruBlocks = thruBlocks,
        )

        if (itemEntities.isEmpty()) {
            cachedRenderEntries = emptyList()
            clearGlowBuckets()
            return@handler
        }

        val stacks = player.openContainer?.inventory ?: emptyList()
        val entries = ArrayList<ItemRenderEntry>(itemEntities.size)

        for (entityItem in itemEntities) {
            val itemStack = entityItem.entityItem
            val isUseful = highlightUseful && InventoryCleaner.isStackUseful(
                itemStack,
                stacks,
                mapOf(itemStack to entityItem),
            )

            val entryColor = if (isUseful) Color.green else color

            var text: String? = null
            var textWidth = 0f

            if (itemText) {
                val itemTextTagFormatted = when (itemTextTag) {
                    "x" -> "x${itemStack.stackSize}"
                    "[]" -> "[${itemStack.stackSize}]"
                    else -> "(${itemStack.stackSize})"
                }

                val entryText = if (itemCounts) {
                    "${itemStack.displayName} $itemTextTagFormatted"
                } else {
                    itemStack.displayName
                }

                text = entryText
                textWidth = font.getStringWidth(entryText) * 0.5f
            }

            entries += ItemRenderEntry(entityItem, entryColor, text, textWidth)
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
        if (entries.isEmpty()) {
            return@handler
        }

        val renderText = itemText
        val renderBoxes = mode != "Glow"
        val outline = mode == "Box"
        val renderManager = mc.renderManager
        val frustumLocal = if (renderText) getFrustum() else null
        if (renderText) {
            frustumLocal?.setPosition(renderManager.renderPosX, renderManager.renderPosY, renderManager.renderPosZ)
        }

        for (entry in entries) {
            val text = entry.text
            if (renderText && text != null && (frustumLocal == null || frustumLocal.isBoundingBoxInFrustum(entry.entity.entityBoundingBox))) {
                renderEntityText(entry.entity, text, entry.textWidth, entry.color)
            }

            if (renderBoxes) {
                drawEntityBox(entry.entity, entry.color, outline)
            }
        }
    }

    val onRender2D = handler<Render2DEvent> { event ->
        if (mode != "Glow" || mc.theWorld == null || mc.thePlayer == null || activeGlowKeys.isEmpty())
            return@handler

        for (key in activeGlowKeys) {
            val list = glowBuckets[key] ?: continue
            if (list.isEmpty()) {
                continue
            }

            GlowShader.startDraw(event.partialTicks, glowRenderScale)

            for (entityItem in list) {
                mc.renderManager.renderEntityStatic(entityItem, event.partialTicks, true)
            }

            GlowShader.stopDraw(glowColors[key] ?: color, glowRadius, glowFade, glowTargetAlpha)
        }
    }

    private fun renderEntityText(entity: EntityItem, text: String, textWidth: Float, color: Color) {
        val thePlayer = mc.thePlayer ?: return
        val renderManager = mc.renderManager
        val rotateX = if (mc.gameSettings.thirdPersonView == 2) -1.0f else 1.0f

        glPushAttrib(GL_ENABLE_BIT)
        glPushMatrix()

        // Translate to entity position
        val (x, y, z) = entity.interpolatedPosition(entity.lastTickPos) - renderManager.renderPos

        glTranslated(x, y, z)

        glRotatef(-renderManager.playerViewY, 0F, 1F, 0F)
        glRotatef(renderManager.playerViewX * rotateX, 1F, 0F, 0F)

        disableGlCap(GL_LIGHTING, GL_DEPTH_TEST)
        enableGlCap(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        val fontRenderer = font

        // Scale
        val scale = ((thePlayer.getDistanceToEntity(entity) / 4F).coerceAtLeast(1F) / 150F) * scale
        glScalef(-scale, -scale, scale)

        // Draw text
        fontRenderer.drawString(
            text,
            1F + -textWidth,
            if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F,
            color.rgb,
            fontShadow,
        )

        resetCaps()
        glPopMatrix()
        glPopAttrib()
    }

    private fun getItemColor(itemStack: ItemStack): Color {
        return when (itemStack.item) {
            Items.diamond -> Color(0, 255, 255)
            Items.gold_ingot -> Color(255, 215, 0)
            Items.iron_ingot -> Color(192, 192, 192)
            Items.wooden_sword, Items.wooden_pickaxe, Items.wooden_axe -> Color(139, 69, 19)
            Items.stone_sword, Items.stone_pickaxe, Items.stone_axe -> Color(169, 169, 169)
            Items.chainmail_chestplate -> Color(105, 105, 105)
            else -> color
        }
    }

    override fun handleEvents() =
        super.handleEvents() || (InventoryCleaner.handleEvents() && InventoryCleaner.highlightUseful)

    private data class ItemRenderEntry(
        val entity: EntityItem,
        val color: Color,
        val text: String?,
        val textWidth: Float,
    )
}
