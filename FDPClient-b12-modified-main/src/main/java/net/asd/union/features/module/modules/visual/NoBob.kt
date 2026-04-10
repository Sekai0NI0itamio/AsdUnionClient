/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.MotionEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category

object NoBob : Module("NoBob", Category.VISUAL, gameDetecting = false, hideModule = false) {

    val onMotion = handler<MotionEvent> {
        mc.thePlayer?.distanceWalkedModified = -1f
    }
}
