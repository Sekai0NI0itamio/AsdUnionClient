/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.int
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.movement.MovementUtils.serverOnGround
import net.asd.union.utils.timing.MSTimer
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.potion.Potion

object Regen : Module("Regen", Category.PLAYER) {

    private val mode by choices("Mode", arrayOf("Vanilla", "Spartan"), "Vanilla")
    private val speed by int("Speed", 100, 1..100) { mode == "Vanilla" }

    private val delay by int("Delay", 0, 0..10000)
    private val health by int("Health", 18, 0..20)
    private val food by int("Food", 18, 0..20)

    private val noAir by boolean("NoAir", false)
    private val potionEffect by boolean("PotionEffect", false)

    private val timer = MSTimer()

    private var resetTimer = false

    val onUpdate = handler<UpdateEvent> {
        if (resetTimer) {
            mc.timer.timerSpeed = 1F
        } else {
            resetTimer = false
        }

        val thePlayer = mc.thePlayer ?: return@handler

        if (
            !mc.playerController.gameIsSurvivalOrAdventure()
            || noAir && !serverOnGround
            || thePlayer.foodStats.foodLevel <= food
            || !thePlayer.isEntityAlive
            || thePlayer.health >= health
            || (potionEffect && !thePlayer.isPotionActive(Potion.regeneration))
            || !timer.hasTimePassed(delay)
        ) return@handler

        when (mode.lowercase()) {
            "vanilla" -> {
                repeat(speed) {
                    sendPacket(C03PacketPlayer(serverOnGround))
                }
            }

            "spartan" -> {
                if (!thePlayer.isMoving && serverOnGround) {
                    repeat(9) {
                        sendPacket(C03PacketPlayer(serverOnGround))
                    }

                    mc.timer.timerSpeed = 0.45F
                    resetTimer = true
                }
            }
        }

        timer.reset()
    }
}
