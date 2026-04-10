/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.render.ColorUtils
import net.asd.union.config.choices
import net.asd.union.config.int
import java.awt.Color

object Glint: Module("Glint", Category.VISUAL, hideModule = false) {

    private val modeValue by choices("Mode", arrayOf("Rainbow", "Custom"), "Custom")
    private val redValue by int("Red", 255, 0.. 255) { modeValue == "Custom" }
    private val greenValue by int("Green", 0, 0.. 255) { modeValue == "Custom" }
    private val blueValue by int("Blue", 0, 0.. 255) { modeValue == "Custom" }

    fun getColor(): Color {
        return when (modeValue.lowercase()) {
            "rainbow" -> ColorUtils.rainbow(10, 0.9F)
            else -> Color(redValue, greenValue, blueValue)
        }
    }
}