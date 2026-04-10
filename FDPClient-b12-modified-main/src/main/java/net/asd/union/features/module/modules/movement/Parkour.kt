/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.event.MovementInputEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.simulation.SimulatedPlayer

object Parkour : Module("Parkour", Category.MOVEMENT, subjective = true, gameDetecting = false, hideModule = false) {

    val onMovementInput = handler<MovementInputEvent> { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        val simPlayer = SimulatedPlayer.fromClientPlayer(event.originalInput)

        simPlayer.tick()

        if (thePlayer.isMoving && thePlayer.onGround && !thePlayer.isSneaking && !mc.gameSettings.keyBindSneak.isKeyDown && !simPlayer.onGround) {
            event.originalInput.jump = true
        }

    }
}
