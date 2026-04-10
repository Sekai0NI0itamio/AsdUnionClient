/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.float

object NoFOV : Module("NoFOV", Category.VISUAL, gameDetecting = false, hideModule = false) {
    val fov by float("FOV", 1f, 0f..1.5f)
}
