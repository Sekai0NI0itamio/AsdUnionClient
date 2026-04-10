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
import net.asd.union.features.module.modules.player.InventoryCleaner
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.attack.EntityUtils.isLookingOnEntities
import net.asd.union.utils.client.EntityLookup
import net.asd.union.utils.extensions.*
import net.asd.union.utils.render.ColorUtils.rainbow
import net.asd.union.utils.render.RenderUtils.disableGlCap
import net.asd.union.utils.render.RenderUtils.drawEntityBox
import net.asd.union.utils.render.RenderUtils.enableGlCap
import net.asd.union.utils.render.RenderUtils.resetCaps
import net.asd.union.utils.render.shader.shaders.GlowShader
import net.asd.union.utils.rotation.RotationUtils.isEntityHeightVisible
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import org.lwjgl.opengl.GL11.*
import java.awt.Color
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

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

    private val itemEntities by EntityLookup<EntityItem>()
        .filter { mc.thePlayer.getDistanceSqToEntity(it) <= maxRenderDistanceSq }
        .filter { !onLook || isLookingOnEntities(it, maxAngleDifference.toDouble()) }
        .filter { thruBlocks || isEntityHeightVisible(it) }

    val onRender3D = handler<Render3DEvent> {
        if (mc.theWorld == null || mc.thePlayer == null)
            return@handler

        for (entityItem in itemEntities) {
            val isUseful =
                InventoryCleaner.handleEvents() && InventoryCleaner.highlightUseful && InventoryCleaner.isStackUseful(
                    entityItem.entityItem,
                    mc.thePlayer.openContainer.inventory,
                    mapOf(entityItem.entityItem to entityItem)
                )

            if (itemText) {
                renderEntityText(entityItem, if (isUseful) Color.green else color)
            }

            if (mode == "Glow")
                continue

            // Only render green boxes on useful items, if ItemESP is enabled, render boxes of ItemESP.color on useless items as well
            drawEntityBox(entityItem, if (isUseful) Color.green else color, mode == "Box")
        }
    }

    val onRender2D = handler<Render2DEvent> { event ->
        if (mode != "Glow")
            return@handler

        for (entityItem in itemEntities) {
            val isUseful =
                InventoryCleaner.handleEvents() && InventoryCleaner.highlightUseful && InventoryCleaner.isStackUseful(
                    entityItem.entityItem,
                    mc.thePlayer.openContainer.inventory,
                    mapOf(entityItem.entityItem to entityItem)
                )

            GlowShader.startDraw(event.partialTicks, glowRenderScale)

            mc.renderManager.renderEntityStatic(entityItem, event.partialTicks, true)

            // Only render green boxes on useful items, if ItemESP is enabled, render boxes of ItemESP.color on useless items as well
            GlowShader.stopDraw(if (isUseful) Color.green else color, glowRadius, glowFade, glowTargetAlpha)
        }
    }

    private fun renderEntityText(entity: EntityItem, color: Color) {
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

        val itemStack = entity.entityItem
        val itemTextTagFormatted = when (itemTextTag) {
            "x" -> "x${itemStack.stackSize}"
            "[]" -> "[${itemStack.stackSize}]"
            else -> "(${itemStack.stackSize})"
        }
        val text = if (itemCounts) itemStack.displayName + " $itemTextTagFormatted" else itemStack.displayName

        // Draw text
        val width = fontRenderer.getStringWidth(text) * 0.5f
        fontRenderer.drawString(
            text, 1F + -width, if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F, color.rgb, fontShadow
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
}