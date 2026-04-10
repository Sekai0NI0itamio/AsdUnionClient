/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.event.Render3DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.extensions.interpolatedPosition
import net.asd.union.utils.extensions.prevPos
import net.asd.union.utils.render.ColorSettingsInteger
import net.asd.union.utils.render.RenderUtils.drawDome
import net.asd.union.utils.render.RenderUtils.drawEntityBox
import net.minecraft.entity.item.EntityTNTPrimed
import org.lwjgl.opengl.GL11.*
import java.awt.Color

object TNTESP : Module("TNTESP", Category.VISUAL, spacedName = "TNT ESP", hideModule = false) {

    private val dangerZoneDome by boolean("DangerZoneDome", false)
    private val mode by choices("Mode", arrayOf("Lines", "Triangles", "Filled"), "Lines") { dangerZoneDome }
    private val lineWidth by float("LineWidth", 1F, 0.5F..5F) { mode == "Lines" }
    private val colors = ColorSettingsInteger(this, "Dome") { dangerZoneDome }

    private val renderModes = mapOf("Lines" to GL_LINES, "Triangles" to GL_TRIANGLES, "Filled" to GL_QUADS)

    val onRender3D = handler<Render3DEvent> {
        val renderMode = renderModes[mode] ?: return@handler
        val color = colors.color()

        val width = lineWidth.takeIf { mode == "Lines" }

        mc.theWorld.loadedEntityList.forEach {
            if (it !is EntityTNTPrimed) return@forEach

            if (dangerZoneDome) {
                drawDome(it.interpolatedPosition(it.prevPos), 8.0, 8.0, width, color, renderMode)
            }

            drawEntityBox(it, Color.RED, false)
        }
    }
}