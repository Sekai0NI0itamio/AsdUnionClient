package net.asd.union.utils.performance

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.GL11

object StartupProgressRenderer {
    @Volatile
    private var displayReady = false

    @Volatile
    private var hasShownStatisticsScreen = false

    @Volatile
    private var hasRenderedBrandText = false

    @JvmStatic
    fun setDisplayReady() {
        displayReady = true
    }

    @JvmStatic
    fun render() {
        val snapshot = StartupProgress.snapshot()
        if (!displayReady || !Display.isCreated() || (!snapshot.active && snapshot.completed < snapshot.total)) {
            return
        }

        val mc = Minecraft.getMinecraft() ?: return
        val font = mc.fontRendererObj
        val scaledResolution = runCatching { ScaledResolution(mc) }.getOrNull()
        val width = scaledResolution?.scaledWidth ?: Display.getWidth()
        val height = scaledResolution?.scaledHeight ?: Display.getHeight()
        if (width <= 0 || height <= 0) {
            return
        }

        mc.entityRenderer?.setupOverlayRendering() ?: run {
            GlStateManager.clear(256)
            GlStateManager.matrixMode(5889)
            GlStateManager.loadIdentity()
            GlStateManager.ortho(0.0, width.toDouble(), height.toDouble(), 0.0, 1000.0, 3000.0)
            GlStateManager.matrixMode(5888)
            GlStateManager.loadIdentity()
            GlStateManager.translate(0.0f, 0.0f, -2000.0f)
        }

        GlStateManager.disableLighting()
        GlStateManager.disableFog()
        GlStateManager.disableDepth()
        GlStateManager.disableCull()
        GlStateManager.enableTexture2D()
        GlStateManager.color(1f, 1f, 1f, 1f)

        Gui.drawRect(0, 0, width, height, -0x1000000)

        val displayPercent = calculateDisplayPercent(snapshot)
        val isIntroPhase = displayPercent < 40
        when {
            isIntroPhase -> {
                hasShownStatisticsScreen = false
                hasRenderedBrandText = drawBranding(font, width, height) || hasRenderedBrandText
                flushDisplay()
            }

            !hasRenderedBrandText -> {
                drawBranding(font, width, height)
                hasRenderedBrandText = font != null
                flushDisplay()
            }

            else -> {
                if (!hasShownStatisticsScreen) {
                    hasShownStatisticsScreen = true
                }

                val lineHeight = font?.FONT_HEIGHT ?: 10
                var y = (height * 0.2f).toInt()
                val title = "Statistical Progress..."

                if (font != null) {
                    val titleX = (width - font.getStringWidth(title)) / 2
                    font.drawStringWithShadow(title, titleX.toFloat(), y.toFloat(), 0xFFFFFF)
                    y += lineHeight + 6

                    val progressLine = "Progress: ${snapshot.completed}/${snapshot.total} ($displayPercent%)"
                    val remainingLine = "Remaining: ${snapshot.remaining}"
                    val currentLine = "Current: ${snapshot.currentLabel}"
                    font.drawStringWithShadow(progressLine, 20f, y.toFloat(), 0xFFFFFF)
                    y += lineHeight + 2
                    font.drawStringWithShadow(remainingLine, 20f, y.toFloat(), 0xFFFFFF)
                    y += lineHeight + 2
                    font.drawStringWithShadow(currentLine, 20f, y.toFloat(), 0xFFFFFF)
                    y += lineHeight + 10
                } else {
                    y += lineHeight + 10
                }

                val barWidth = minOf(240, width - 40)
                val barX = (width - barWidth) / 2
                Gui.drawRect(barX, y, barX + barWidth, y + 6, -14540254)
                val filled = (barWidth * (displayPercent / 100f)).toInt()
                Gui.drawRect(barX, y, barX + filled, y + 6, -1)

                if (font != null) {
                    y += 14

                    for (step in snapshot.steps) {
                        val prefix = when (step.status) {
                            StartupProgress.Status.COMPLETE -> "[x]"
                            StartupProgress.Status.ACTIVE -> "[>]"
                            StartupProgress.Status.PENDING -> "[ ]"
                        }

                        font.drawStringWithShadow("$prefix ${step.label}", 20f, y.toFloat(), 0xFFFFFF)
                        y += lineHeight + 2
                        if (y > height - 10) {
                            break
                        }
                    }
                }

                flushDisplay()
            }
        }
    }

    private fun calculateDisplayPercent(snapshot: StartupProgress.Snapshot): Int {
        return when (snapshot.currentIndex) {
            0 -> (snapshot.subProgress * 13f).toInt().coerceIn(0, 12)
            1 -> (13 + (snapshot.subProgress * 13f).toInt()).coerceIn(13, 25)
            2 -> (26 + (snapshot.subProgress * 14f).toInt()).coerceIn(26, 39)
            3 -> (40 + (snapshot.subProgress * 20f).toInt()).coerceIn(40, 60)
            4 -> (61 + (snapshot.subProgress * 34f).toInt()).coerceIn(61, 95)
            5 -> if (snapshot.active) 96 else 100
            else -> snapshot.percent.coerceIn(0, 100)
        }
    }

    private fun drawBranding(font: FontRenderer?, width: Int, height: Int): Boolean {
        if (font == null) {
            return false
        }

        val clientName = "AsdUnionTech"
        val centerX = (width - font.getStringWidth(clientName)) / 2
        val centerY = height / 2 - font.FONT_HEIGHT / 2
        font.drawStringWithShadow(clientName, centerX.toFloat(), centerY.toFloat(), 0xFFFFFF)

        val credit = "Built by Itamio."
        val creditY = (height - font.FONT_HEIGHT - 6).toFloat()
        font.drawStringWithShadow(credit, 6f, creditY, 0xFFFFFF)
        return true
    }

    private fun flushDisplay() {
        runCatching {
            Display.update()
            Display.sync(60)
            GL11.glFlush()
        }
    }
}
