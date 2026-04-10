/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.speedmodes.other

import net.asd.union.features.module.modules.movement.Speed.bmcDamageBoost
import net.asd.union.features.module.modules.movement.Speed.bmcLowHop
import net.asd.union.features.module.modules.movement.Speed.damageLowHop
import net.asd.union.features.module.modules.movement.Speed.fullStrafe
import net.asd.union.features.module.modules.movement.Speed.safeY
import net.asd.union.features.module.modules.movement.speedmodes.SpeedMode
import net.asd.union.utils.extensions.isInLiquid
import net.asd.union.utils.movement.MovementUtils.airTicks
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump
import net.minecraft.potion.Potion

object BlocksMCHop : SpeedMode("BlocksMCHop") {

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (player.isInLiquid || player.isInWeb || player.isOnLadder) return

        if (player.isMoving) {
            if (player.onGround) {
                player.tryJump()
            } else {
                if (fullStrafe) {
                    strafe(speed = speed - 0.006F)
                } else {
                    if (airTicks >= 6) {
                        strafe()
                    }
                }

                if ((player.getActivePotionEffect(Potion.moveSpeed)?.amplifier ?: 0) > 0 && airTicks == 3) {
                    player.motionX *= 1.12
                    player.motionZ *= 1.12
                }

                if (bmcLowHop && airTicks == 4) {
                    if (safeY) {
                        if (player.posY % 1.0 == 0.16610926093821377) {
                            player.motionY = -0.09800000190734863
                        }
                    } else {
                        player.motionY = -0.09800000190734863
                    }
                }

                if (player.hurtTime == 9 && bmcDamageBoost) {
                    strafe(speed = 1F)
                }

                if (damageLowHop && player.hurtTime >= 1) {
                    if (player.motionY > 0) {
                        player.motionY -= 0.15
                    }
                }
            }
        }
    }

}