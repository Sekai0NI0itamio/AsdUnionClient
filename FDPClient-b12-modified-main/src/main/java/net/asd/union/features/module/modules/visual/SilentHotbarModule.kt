/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.boolean

object SilentHotbarModule : Module("SilentHotbar", Category.VISUAL) {
    val keepHighlightedName by boolean("KeepHighlightedName", false)
    val keepHotbarSlot by boolean("KeepHotbarSlot", false)
    val keepItemInHandInFirstPerson by boolean("KeepItemInHandInFirstPerson", false)
    val keepItemInHandInThirdPerson by boolean("KeepItemInHandInThirdPerson", false)
}