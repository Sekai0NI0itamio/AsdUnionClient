/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.boolean
import net.asd.union.event.loopHandler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.block.BlockUtils
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.minecraft.init.Blocks.lava
import net.minecraft.init.Blocks.water
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action
import net.minecraft.util.EnumFacing

object NoFluid : Module("NoFluid", Category.MOVEMENT) {

    val waterValue by boolean("Water", true)
    val lavaValue by boolean("Lava", true)
    private val oldGrim by boolean("OldGrim", false)

    val onUpdate = loopHandler {
        if ((waterValue || lavaValue) && oldGrim) {
            BlockUtils.searchBlocks(2, setOf(water, lava)).keys.forEach {
                // TODO:only do this for blocks that player touched
                sendPacket(C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, it, EnumFacing.DOWN))
            }
        }
    }
}
