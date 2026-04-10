/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player

import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.utils.timing.MSTimer
import net.minecraft.item.ItemFishingRod

object AutoFish : Module("AutoFish", Category.PLAYER, subjective = true, gameDetecting = false, hideModule = false) {

    private val rodOutTimer = MSTimer()


    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer

        if (thePlayer?.heldItem == null || mc.thePlayer.heldItem.item !is ItemFishingRod)
            return@handler

        val fishEntity = thePlayer.fishEntity

        if (rodOutTimer.hasTimePassed(500) && fishEntity == null || (fishEntity != null && fishEntity.motionX == 0.0 && fishEntity.motionZ == 0.0 && fishEntity.motionY != 0.0)) {
            mc.rightClickMouse()
            rodOutTimer.reset()
        }
    }
}
