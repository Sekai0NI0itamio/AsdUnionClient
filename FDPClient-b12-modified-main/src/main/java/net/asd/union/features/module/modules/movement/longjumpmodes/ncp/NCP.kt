/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.longjumpmodes.ncp

import net.asd.union.event.MoveEvent
import net.asd.union.features.module.modules.movement.LongJump.canBoost
import net.asd.union.features.module.modules.movement.LongJump.jumped
import net.asd.union.features.module.modules.movement.LongJump.ncpBoost
import net.asd.union.features.module.modules.movement.longjumpmodes.LongJumpMode
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.extensions.isMoving

object NCP : LongJumpMode("NCP") {
    override fun onUpdate() {
        speed *= if (canBoost) ncpBoost else 1f
        canBoost = false
    }

    override fun onMove(event: MoveEvent) {
        if (!mc.thePlayer.isMoving && jumped) {
            mc.thePlayer.motionX = 0.0
            mc.thePlayer.motionZ = 0.0
            event.zeroXZ()
        }
    }
}