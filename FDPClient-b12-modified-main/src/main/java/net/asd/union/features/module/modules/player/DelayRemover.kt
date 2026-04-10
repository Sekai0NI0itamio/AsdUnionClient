/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player


import net.asd.union.event.MotionEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.movement.MovementUtils.updateControls
import net.asd.union.config.boolean
import net.asd.union.event.handler

object DelayRemover : Module("DelayRemover", Category.PLAYER, hideModule = false) {

   // val jumpDelay by boolean("NoJumpDelay", false)
  //  val jumpDelayTicks by IntegerValue("JumpDelayTicks", 0, 0.. 4) { jumpDelay }

    val noClickDelay by boolean("NoClickDelay", true)

    val blockBreakDelay by boolean("NoBlockHitDelay", false)

    val noSlowBreak by boolean("NoSlowBreak", false)
    val air by boolean("Air", true) { noSlowBreak }
    val water by boolean("Water", false) { noSlowBreak }

    val exitGuiValue by boolean("NoExitGuiDelay", true)

    private var prevGui = false


    val onMotion = handler<MotionEvent> {
        if (mc.thePlayer != null && mc.theWorld != null && noClickDelay) {
            mc.leftClickCounter = 0
        }

        if (blockBreakDelay) {
            mc.playerController.blockHitDelay = 0
        }

        if (mc.currentScreen == null && exitGuiValue) {
            if (prevGui) updateControls()
            prevGui = false
        } else {
            prevGui = true
        }
    }

}
