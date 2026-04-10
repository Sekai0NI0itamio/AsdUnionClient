/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.boolean
import net.asd.union.config.int
import net.asd.union.event.MoveEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.block.block
import net.asd.union.utils.movement.FallingPlayer
import net.minecraft.block.BlockAir
import net.minecraft.util.BlockPos

object SafeWalk : Module("SafeWalk", Category.MOVEMENT, hideModule = false) {

    private val airSafe by boolean("AirSafe", false)
    private val maxFallDistanceValue = int("MaxFallDistance", 5, 0..100)

    private var lastGroundY: Double? = null
    private var lastCollisionY: Int? = null

    val onMove = handler<MoveEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        if (player.capabilities.allowFlying || player.capabilities.isFlying
            || !mc.playerController.gameIsSurvivalOrAdventure()
        ) return@handler

        if (!maxFallDistanceValue.isMinimal() && player.onGround && BlockPos(player).down().block !is BlockAir) {
            lastGroundY = player.posY
            lastCollisionY = FallingPlayer(player, true).findCollision(60)?.pos?.y
        }

        if (airSafe || player.onGround) {
            event.isSafeWalk = maxFallDistanceValue.isMinimal()
                    || (lastGroundY != null && lastCollisionY != null
                    && lastGroundY!! - lastCollisionY!! > maxFallDistanceValue.get() + 1)
        }
    }
}
