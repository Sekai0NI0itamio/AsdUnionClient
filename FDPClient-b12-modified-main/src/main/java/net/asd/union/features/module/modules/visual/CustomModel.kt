/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.boolean
import net.asd.union.config.choices

object CustomModel : Module("CustomModel", Category.VISUAL, hideModule = false) {
    val mode by choices("Mode", arrayOf("Imposter", "Rabbit", "Freddy", "None"), "Imposter")

    val rotatePlayer by  boolean("RotatePlayer", false)

    override val tag: String
        get() = mode
}