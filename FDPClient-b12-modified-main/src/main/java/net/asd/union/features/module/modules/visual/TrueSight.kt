/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean
import net.asd.union.event.handler

object TrueSight : Module("TrueSight", Category.VISUAL) {
    val barriers by boolean("Barriers", true)
    val entities by boolean("Entities", true)

    val onUpdate = handler<UpdateEvent> {
        if (barriers && mc.gameSettings.particleSetting == 2) {
            mc.gameSettings.particleSetting = 1
        }
    }
}