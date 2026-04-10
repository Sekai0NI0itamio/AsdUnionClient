/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.boolean

object OverrideRaycast : Module("OverrideRaycast", Category.OTHER, gameDetecting = false, hideModule = false) {
    private val alwaysActive by boolean("AlwaysActive", true)

    fun shouldOverride() = handleEvents() || alwaysActive
}