/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.event.loopHandler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.client.settings.GameSettings

object AutoWalk : Module("AutoWalk", Category.MOVEMENT, subjective = true, gameDetecting = false, hideModule = false) {

    val onUpdate = loopHandler {
        mc.gameSettings.keyBindForward.pressed = true
    }

    override fun onDisable() {
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindForward))
            mc.gameSettings.keyBindForward.pressed = false
    }
}
