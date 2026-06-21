/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.event.*
import net.asd.union.event.handler
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.utils.extensions.isMoving
import net.asd.union.config.choices
import net.asd.union.config.float

object Timer : Module("Timer", Category.MOVEMENT, gameDetecting = false, hideModule = false) {

    override fun handleEvents() = true

    /**
     * Modes:
     *  - OnMove  : speed up timer only while moving
     *  - NoMove  : speed up timer only while standing still
     *  - Always  : always apply the speed multiplier
     *  - Freeze  : pause game timer completely (timerSpeed = 0), allowing
     *              inputs to still register so the module can be toggled off.
     *              Server packets are still sent to prevent desync.
     */
    private val mode by choices("Mode", arrayOf("OnMove", "NoMove", "Always", "Freeze"), "OnMove")
    private val speed by float("Speed", 2F, 0.005F..10F, step = 0.005F)

    val isFreezing: Boolean
        get() = state && mode == "Freeze"

    override fun onDisable() {
        mc.timer.timerSpeed = 1F
        mc.timer.renderPartialTicks = 1F
    }

    val onPreTick = handler<PreTickEvent> {
        if (!state) {
            mc.timer.timerSpeed = 1F
            return@handler
        }
        when (mode) {
            "Always"  -> mc.timer.timerSpeed = speed
            "OnMove"  -> mc.timer.timerSpeed = if (mc.thePlayer?.isMoving == true) speed else 1F
            "NoMove"  -> mc.timer.timerSpeed = if (mc.thePlayer?.isMoving == false) speed else 1F
            "Freeze"  -> mc.timer.timerSpeed = 0f
        }
    }

    private val onTickEnd = handler<TickEndEvent> {
        if (!state) {
            mc.timer.timerSpeed = 1F
        }
    }

    val onWorld = handler<WorldEvent> { event ->
        if (event.worldClient != null) return@handler
        state = false
    }
}
