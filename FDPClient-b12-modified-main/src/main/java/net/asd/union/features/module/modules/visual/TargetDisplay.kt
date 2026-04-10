/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.boolean
import net.asd.union.config.color
import net.asd.union.config.font
import net.asd.union.config.int
import net.asd.union.event.Render2DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.combat.KillAura
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.render.RenderUtils.drawRect
import net.asd.union.utils.render.RenderUtils.drawRectWithBorder
import java.awt.Color
import kotlin.math.roundToInt

object TargetDisplay : Module("TargetDisplay", Category.VISUAL) {

    private val background by boolean("Background", true)
    private val backgroundColor by color("BackgroundColor", Color(0, 0, 0, 150)) { background }
    private val border by boolean("Border", true)
    private val borderColor by color("BorderColor", Color(0, 160, 255)) { border }
    private val shadow by boolean("Shadow", true)
    private val spacing by int("Spacing", 10, 0..50)
    private val font by font("Font", Fonts.font40)

    val onRender2D = handler<Render2DEvent> {
        val target = KillAura.target ?: return@handler
        if (!KillAura.state) return@handler

        val player = mc.thePlayer ?: return@handler
        val distance = player.getDistanceToEntity(target).roundToInt()
        val health = target.health
        val ratio = (health / 20F).roundToInt()

        val healthText = when {
            health >= 11F -> "\u2764 ${health.roundToInt()} \u00A7a\u2715 $ratio"
            health >= 4F -> "\u2764 ${health.roundToInt()} \u00A7e\u2715 $ratio"
            else -> "\u2764 ${health.roundToInt()} \u00A7c\u2715 $ratio"
        }

        val nameLine = "\u00A77Target: \u00A7f${target.name}"
        val healthLine = "\u00A77Health: $healthText"
        val distanceLine = "\u00A77Distance: \u00A7f${distance}m"

        val lineHeight = font.FONT_HEIGHT + 4
        val padding = 6
        val maxWidth = maxOf(
            font.getStringWidth(nameLine),
            font.getStringWidth(healthLine),
            font.getStringWidth(distanceLine),
        )
        val boxWidth = maxWidth + padding * 2
        val boxHeight = lineHeight * 3 + padding * 2
        val posX = spacing.toFloat()
        val posY = spacing.toFloat()

        if (background) {
            val left = posX - padding
            val top = posY - padding
            val right = posX + boxWidth - padding
            val bottom = posY + boxHeight - padding

            if (border) {
                drawRectWithBorder(left, top, right, bottom, 1.5F, borderColor.rgb, backgroundColor.rgb)
            } else {
                drawRect(left, top, right, bottom, backgroundColor.rgb)
            }
        }

        var currentY = posY + padding
        font.drawString(nameLine, posX, currentY, Color.WHITE.rgb, shadow)
        currentY += lineHeight
        font.drawString(healthLine, posX, currentY, Color.WHITE.rgb, shadow)
        currentY += lineHeight
        font.drawString(distanceLine, posX, currentY, Color.WHITE.rgb, shadow)
    }
}
