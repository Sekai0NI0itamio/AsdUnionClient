/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.FDPClient.hud
import net.asd.union.event.PacketEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.WorldEvent
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.features.module.modules.combat.KillAura
import net.asd.union.features.module.modules.movement.Flight
import net.asd.union.features.module.modules.movement.Speed
import net.asd.union.features.module.modules.player.scaffolds.*
import net.asd.union.utils.client.chat
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.event.handler
import net.minecraft.network.play.server.S08PacketPlayerPosLook

object AutoDisable : Module("AutoDisable", Category.OTHER, gameDetecting = false, hideModule = false) {
    private val modulesList = hashSetOf(KillAura, Scaffold, Flight, Speed)

    private val onFlagged by boolean("onFlag", true)
    private val onWorldChange by boolean("onWorldChange", false)
    private val onDeath by boolean("onDeath", false)

    private val warn by choices("Warn", arrayOf("Chat", "Notification"), "Chat")

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is S08PacketPlayerPosLook && onFlagged) {
            disabled("flagged")
        }
    }

    val onUpdate = handler<UpdateEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (onDeath && player.isDead) {
            disabled("deaths")
        }
    }

           val onWorld = handler<WorldEvent> { event ->
        if (onWorldChange) {
            disabled("world changed")
        }
    }

    private fun disabled(reason: String) {
        val enabledModules = modulesList.filter { it.state }

        if (enabledModules.isNotEmpty()) {
            enabledModules.forEach { module ->
                module.state = false
            }

            if (warn == "Chat") {
                chat("§eModules have been disabled due to §c$reason")
            } else {
                hud.addNotification(Notification("Modules have been disabled due to $reason", "!!!", Type.INFO, 60))
            }
        }
    }

    fun addModule(module: Module) {
        if (!modulesList.contains(module)) {
            modulesList.add(module)
        }
    }

    fun removeModule(module: Module) {
        modulesList.remove(module)
    }

    fun getModules(): Collection<Module> {
        return modulesList
    }
}