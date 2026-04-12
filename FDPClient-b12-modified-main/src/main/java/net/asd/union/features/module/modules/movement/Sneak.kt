/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.event.EventState
import net.asd.union.event.MotionEvent
import net.asd.union.event.WorldEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.isMoving
import net.minecraft.client.settings.GameSettings
import net.minecraft.client.settings.KeyBinding
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SNEAKING
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SNEAKING

object Sneak : Module("Sneak", Category.MOVEMENT, hideModule = false) {

    val mode by choices("Mode", arrayOf("Legit", "Vanilla", "Switch", "MineSecure"), "Legit")
    val stopMove by boolean("StopMove", false)

    private var sneaking = false

    private fun holdSneak() {
        val bind = mc.gameSettings.keyBindSneak
        KeyBinding.setKeyBindState(bind.keyCode, true)
    }

    private fun releaseSneakKey() {
        val bind = mc.gameSettings.keyBindSneak
        KeyBinding.setKeyBindState(bind.keyCode, GameSettings.isKeyDown(bind))
    }

    val onMotion = handler<MotionEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (stopMove && player.isMoving) {
            when (mode.lowercase()) {
                "legit" -> releaseSneakKey()

                else -> {
                    if (sneaking) {
                        sendPacket(C0BPacketEntityAction(player, STOP_SNEAKING))
                        sneaking = false
                    }
                }
            }

            return@handler
        }

        when (mode.lowercase()) {
            // Holds the user's configured Sneak key bind, like physically holding the key.
            "legit" -> holdSneak()

            "vanilla" -> {
                if (sneaking) return@handler
                sendPacket(C0BPacketEntityAction(player, START_SNEAKING))
                sneaking = true
            }

            "switch" -> {
                when (event.eventState) {
                    EventState.PRE -> sendPackets(
                        C0BPacketEntityAction(player, START_SNEAKING),
                        C0BPacketEntityAction(player, STOP_SNEAKING)
                    )

                    EventState.POST -> sendPackets(
                        C0BPacketEntityAction(player, STOP_SNEAKING),
                        C0BPacketEntityAction(player, START_SNEAKING)
                    )

                    else -> {}
                }
                sneaking = true
            }

            "minesecure" -> {
                if (event.eventState == EventState.PRE) return@handler
                sendPacket(C0BPacketEntityAction(player, START_SNEAKING))
                sneaking = true
            }
        }
    }

    val onWorld = handler<WorldEvent> {
        sneaking = false
        releaseSneakKey()
    }

    override fun onEnable() {
        sneaking = false
        if (mode.lowercase() == "legit") {
            holdSneak()
        }
    }

    override fun onDisable() {
        val player = mc.thePlayer

        when (mode.lowercase()) {
            "legit" -> {}
            "vanilla", "switch", "minesecure" -> player?.let { sendPacket(C0BPacketEntityAction(it, STOP_SNEAKING)) }
        }

        sneaking = false
        releaseSneakKey()
    }
}

