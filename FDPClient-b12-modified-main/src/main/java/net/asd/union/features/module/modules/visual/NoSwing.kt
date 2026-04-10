/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean

object NoSwing : Module("NoSwing", Category.VISUAL, hideModule = false) {
    val serverSide by boolean("ServerSide", true)
}