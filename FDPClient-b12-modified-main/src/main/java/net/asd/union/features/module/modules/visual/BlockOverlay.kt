/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.color
import net.asd.union.config.float
import net.asd.union.event.Render2DEvent
import net.asd.union.event.Render3DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.block.block
import net.asd.union.utils.extensions.*
import net.asd.union.utils.render.RenderUtils.drawBorderedRect
import net.asd.union.utils.render.RenderUtils.drawFilledBox
import net.asd.union.utils.render.RenderUtils.drawSelectionBoundingBox
import net.asd.union.utils.render.RenderUtils.glColor
import net.minecraft.block.Block
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.resetColor
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import org.lwjgl.opengl.GL11.*
import java.awt.Color

object BlockOverlay : Module("BlockOverlay", Category.VISUAL, gameDetecting = false, hideModule = false) {
    private val mode by choices("Mode", arrayOf("Box", "OtherBox", "Outline"), "Box")
    private val depth3D by boolean("Depth3D", false)
    private val thickness by float("Thickness", 2F, 1F..5F)

    val info by boolean("Info", false)

    private val color by color("Color", Color(68, 117, 255, 100))

    val currentBlock: BlockPos?
        get() {
            val world = mc.theWorld ?: return null
            val blockPos = mc.objectMouseOver?.blockPos ?: return null

            if (blockPos.block !in arrayOf(
                    Blocks.air,
                    Blocks.water,
                    Blocks.lava
                ) && world.worldBorder.contains(blockPos)
            )
                return blockPos

            return null
        }

    val onRender3D = handler<Render3DEvent> {
        val blockPos = currentBlock ?: return@handler

        val block = blockPos.block ?: return@handler

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)
        glColor(color)
        glLineWidth(thickness)
        glDisable(GL_TEXTURE_2D)
        if (depth3D) glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        block.setBlockBoundsBasedOnState(mc.theWorld, blockPos)

        val thePlayer = mc.thePlayer ?: return@handler

        val pos = thePlayer.interpolatedPosition(thePlayer.lastTickPos)

        val f = 0.002F.toDouble()

        val axisAlignedBB = block.getSelectedBoundingBox(mc.theWorld, blockPos).expand(f, f, f).offset(-pos)

        if (mode.lowercase() in arrayOf("box", "otherbox"))
            drawFilledBox(axisAlignedBB)
        if (mode.lowercase() in arrayOf("box", "outline"))
            drawSelectionBoundingBox(axisAlignedBB)

        if (depth3D) glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_LINE_SMOOTH)
        glDepthMask(true)
        resetColor()
    }

    val onRender2D = handler<Render2DEvent> {
        if (!info) return@handler

        val blockPos = currentBlock ?: return@handler
        val block = blockPos.block ?: return@handler

        val info = "${block.localizedName} §7ID: ${Block.getIdFromBlock(block)}"
        val (width, height) = ScaledResolution(mc)

        drawBorderedRect(
            width / 2 - 2F,
            height / 2 + 5F,
            width / 2 + Fonts.font40.getStringWidth(info) + 2F,
            height / 2 + 16F,
            3F, Color.BLACK.rgb, Color.BLACK.rgb
        )

        resetColor()
        Fonts.font40.drawString(info, width / 2f, height / 2f + 7f, Color.WHITE.rgb, false)
    }
}