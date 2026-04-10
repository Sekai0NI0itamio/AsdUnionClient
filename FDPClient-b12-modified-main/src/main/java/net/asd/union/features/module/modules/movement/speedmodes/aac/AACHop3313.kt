/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.aac

import net.asd.union.event.EventManager.call
import net.asd.union.event.EventState
import net.asd.union.event.JumpEvent
import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.block.block
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.toRadians
import net.asd.union.utils.movement.MovementUtils.strafe
import net.minecraft.block.BlockCarpet
import kotlin.math.cos
import kotlin.math.sin

object AACHop3313 : SpeedMode("AACHop3.3.13") {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (!thePlayer.isMoving || thePlayer.isInLiquid ||
            thePlayer.isOnLadder || thePlayer.isRiding || thePlayer.hurtTime > 0
        ) return
        if (thePlayer.onGround && thePlayer.isCollidedVertically) {
            // MotionXYZ
            val yawRad = thePlayer.rotationYaw.toRadians()
            thePlayer.motionX -= sin(yawRad) * 0.202f
            thePlayer.motionZ += cos(yawRad) * 0.202f
            thePlayer.motionY = 0.405
            call(JumpEvent(0.405f, EventState.PRE))
            strafe()
        } else if (thePlayer.fallDistance < 0.31f) {
            if (thePlayer.position.block is BlockCarpet) // why?
                return

            // Motion XZ
            thePlayer.jumpMovementFactor = if (thePlayer.moveStrafing == 0f) 0.027f else 0.021f
            thePlayer.motionX *= 1.001
            thePlayer.motionZ *= 1.001

            // Motion Y
            if (!thePlayer.isCollidedHorizontally) thePlayer.motionY -= 0.014999993f
        } else thePlayer.jumpMovementFactor = 0.02f
    }

    override fun onDisable() {
        mc.thePlayer.jumpMovementFactor = 0.02f
    }
}