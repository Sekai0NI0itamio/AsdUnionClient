/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player


import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.block.block
import net.asd.union.utils.timing.MSTimer
import net.asd.union.config.boolean
import net.asd.union.config.float
import net.asd.union.config.int
import net.asd.union.event.handler
import net.minecraft.client.gui.Gui
import net.minecraft.client.settings.GameSettings
import net.minecraft.init.Blocks.air
import net.minecraft.util.BlockPos

object Eagle : Module("Eagle", Category.PLAYER, hideModule = false) {

    private val sneakDelay by int("SneakDelay", 0, 0..100)
    private val onlyWhenLookingDown by boolean("OnlyWhenLookingDown", false)
    private val lookDownThreshold by float("LookDownThreshold", 45f, 0f..90f) { onlyWhenLookingDown }

    private val sneakTimer = MSTimer()
    private var sneakOn = false


    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.onGround && BlockPos(thePlayer).down().block == air) {
            val shouldSneak = !onlyWhenLookingDown || thePlayer.rotationPitch >= lookDownThreshold

            if (shouldSneak && !GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
                if (sneakTimer.hasTimePassed(sneakDelay)) {
                    mc.gameSettings.keyBindSneak.pressed = true
                    sneakTimer.reset()
                    sneakOn = false
                }
            } else {
                mc.gameSettings.keyBindSneak.pressed = false
            }

            sneakOn = true
        } else {
            if (sneakOn) {
                mc.gameSettings.keyBindSneak.pressed = false
                sneakOn = false
            }
        }

        if (!sneakOn && mc.currentScreen !is Gui) mc.gameSettings.keyBindSneak.pressed =
            GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
    }

    override fun onDisable() {
        if (mc.thePlayer == null)
            return

        sneakOn = false

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak))
            mc.gameSettings.keyBindSneak.pressed = false
    }
}
