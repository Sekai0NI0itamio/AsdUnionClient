/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.float
import net.asd.union.event.MoveEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.movement.MovementUtils.strafe

object NoClip : Module("NoClip", Category.MOVEMENT, hideModule = false) {
    val speed by float("Speed", 0.5f, 0f..10f)

    override fun onDisable() {
        mc.thePlayer?.noClip = false
    }

    val onMove = handler<MoveEvent> { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        strafe(speed, stopWhenNoInput = true, event)

        thePlayer.noClip = true
        thePlayer.onGround = false

        thePlayer.capabilities.isFlying = false

        var ySpeed = 0.0

        if (mc.gameSettings.keyBindJump.isKeyDown)
            ySpeed += speed

        if (mc.gameSettings.keyBindSneak.isKeyDown)
            ySpeed -= speed

        thePlayer.motionY = ySpeed
        event.y = ySpeed
    }
}
