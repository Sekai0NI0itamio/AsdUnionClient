/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean
import net.asd.union.config.float

object ItemPhysics: Module("ItemPhysics", Category.VISUAL, hideModule = false) {

    val realistic by boolean("Realistic", false)
    val weight by float("Weight", 0.5F, 0.1F..3F)
    val rotationSpeed by float("RotationSpeed", 1.0F, 0.01F..3F)

}