/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player.nofallmodes.other

import net.asd.union.event.PacketEvent
import net.asd.union.event.Render3DEvent
import net.asd.union.features.module.modules.player.NoFall.autoOff
import net.asd.union.features.module.modules.player.NoFall.checkFallDist
import net.asd.union.features.module.modules.player.NoFall.fakePlayer
import net.asd.union.features.module.modules.player.NoFall.maxFallDist
import net.asd.union.features.module.modules.player.NoFall.minFallDist
import net.asd.union.features.module.modules.player.NoFall.simulateDebug
import net.asd.union.features.module.modules.player.NoFall.state
import net.asd.union.features.module.modules.player.nofallmodes.NoFallMode
import net.asd.union.injection.implementations.IMixinEntity
import net.asd.union.utils.client.BlinkUtils
import net.asd.union.utils.client.chat
import net.asd.union.utils.extensions.*
import net.asd.union.utils.movement.FallingPlayer
import net.asd.union.utils.render.RenderUtils.drawBacktrackBox
import net.asd.union.utils.simulation.SimulatedPlayer
import net.asd.union.utils.timing.TickTimer
import net.minecraft.network.play.client.C03PacketPlayer
import java.awt.Color

object Blink : NoFallMode("Blink") {
    private var blinked = false

    private val tick = TickTimer()

    override fun onDisable() {
        BlinkUtils.unblink()
        blinked = false
        tick.reset()
    }

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val thePlayer = mc.thePlayer ?: return

        if (thePlayer.isDead)
            return

        val simPlayer = SimulatedPlayer.fromClientPlayer(thePlayer.movementInput)

        simPlayer.tick()

        if (simPlayer.onGround && blinked) {
            if (thePlayer.onGround) {
                tick.update()

                if (tick.hasTimePassed(100)) {
                    BlinkUtils.unblink()
                    blinked = false
                    chat("Unblink")

                    if (autoOff) {
                        state = false
                    }
                    tick.reset()
                }
            }
        }

        if (event.packet is C03PacketPlayer) {
            if (blinked && thePlayer.fallDistance > minFallDist.get()) {
                if (thePlayer.fallDistance < maxFallDist.get()) {
                    if (blinked) {
                        event.packet.onGround = thePlayer.ticksExisted % 2 == 0
                    }
                } else {
                    chat("rewriting ground")
                    BlinkUtils.unblink()
                    blinked = false
                    event.packet.onGround = false
                }
            }
        }

        // Re-check #1
        repeat(2) {
            simPlayer.tick()
        }

        if (simPlayer.isOnLadder() || simPlayer.inWater || simPlayer.isInLava() || simPlayer.isInWeb || simPlayer.isCollided)
            return

        if (thePlayer.motionY > 0 && blinked)
            return

        if (simPlayer.onGround)
            return

        // Re-check #2
        if (checkFallDist) {
            repeat(6) {
                simPlayer.tick()
            }
        }

        val fallingPlayer = FallingPlayer(thePlayer)

        if ((checkFallDist && simPlayer.fallDistance > minFallDist.get()) ||
            !checkFallDist && fallingPlayer.findCollision(60) != null && simPlayer.motionY < 0
        ) {
            if (thePlayer.onGround && !blinked) {
                blinked = true

                if (fakePlayer)
                    BlinkUtils.addFakePlayer()

                chat("Blinked")
                BlinkUtils.blink(packet, event)
            }
        }
    }

   override fun onRender3D(event: Render3DEvent) {
        if (!simulateDebug) return

        val thePlayer = mc.thePlayer ?: return

        val simPlayer = SimulatedPlayer.fromClientPlayer(thePlayer.movementInput)

        repeat(4) {
            simPlayer.tick()
        }

        thePlayer.run {
            val targetEntity = thePlayer as IMixinEntity

            if (targetEntity.truePos) {
                val pos = simPlayer.pos - mc.renderManager.renderPos

                val axisAlignedBB = entityBoundingBox.offset(-currPos + pos)

                drawBacktrackBox(axisAlignedBB, Color.BLUE)
            }
        }
    }
}