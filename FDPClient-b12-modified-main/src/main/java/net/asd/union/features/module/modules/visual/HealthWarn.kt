/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.client.hud.HUD.addNotification
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import net.asd.union.config.int
import net.asd.union.event.handler

object HealthWarn: Module("HealthWarn", Category.VISUAL, hideModule = false) {

    private val healthValue by int("Health", 7, 1.. 20)

    private var canWarn = true

    override fun onEnable() {
        canWarn = true
    }

    override fun onDisable() {
        canWarn = true
    }


    val onUpdate = handler<UpdateEvent> {
        if (mc.thePlayer.health <= healthValue) {
            if (canWarn) {
                addNotification(Notification("HP Warning","YOU ARE AT LOW HP!", Type.ERROR, 3000))

                canWarn = false
            }
        } else {
            canWarn = true
        }
    }
}
