/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean

object TargetModule : Module("Target", Category.CLIENT, defaultInArray = false, gameDetecting = false, hideModule = true, canBeEnabled = false) {
    var playerValue by boolean("Player", true)
    var animalValue by boolean("Animal", true)
    var mobValue by boolean("Mob", true)
    var invisibleValue by boolean("Invisible", false)
    var deadValue by boolean("Dead", false)

    override fun handleEvents() = true
}