/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.event.UpdateEvent
import net.asd.union.event.WorldEvent
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.utils.extensions.isMoving
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.event.handler

object Timer : Module("Timer", Category.MOVEMENT, gameDetecting = false, hideModule = false) {

    private val mode by choices("Mode", arrayOf("OnMove", "NoMove", "Always"), "OnMove")
    private val speed by float("Speed", 2F, 0.1F..10F)

    override fun onDisable() {
        if (mc.thePlayer == null)
            return

        mc.timer.timerSpeed = 1F
    }
    
    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode == "Always" || mode == "OnMove" && player.isMoving || mode == "NoMove" && !player.isMoving) {
            mc.timer.timerSpeed = speed
            return@handler
        }

        mc.timer.timerSpeed = 1F
    }

       val onWorld = handler<WorldEvent> { event ->
        if (event.worldClient != null)
            return@handler

        state = false
    }
}