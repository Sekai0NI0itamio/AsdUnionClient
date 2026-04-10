/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.event.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.rotation.RotationSettings
import net.asd.union.utils.rotation.RotationUtils.faceBlock
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.block.BlockUtils.getCenterDistance
import net.asd.union.utils.block.block
import net.asd.union.utils.render.RenderUtils.drawBlockBox
import net.asd.union.config.boolean
import net.asd.union.config.float
import net.minecraft.init.Blocks.air
import net.minecraft.init.Blocks.bedrock
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import java.awt.Color

object CivBreak : Module("CivBreak", Category.OTHER) {

    private val range by float("Range", 5F, 1F..6F)
    private val visualSwing by boolean("VisualSwing", true, subjective = false)

    private val options = RotationSettings(this).withoutKeepRotation()

    private var blockPos: BlockPos? = null
    private var enumFacing: EnumFacing? = null


    val onBlockClick = handler<ClickBlockEvent> { event ->
        blockPos = event.clickedBlock?.takeIf { it.block != bedrock } ?: return@handler

        blockPos = event.clickedBlock ?: return@handler
        enumFacing = event.enumFacing ?: return@handler

        // Break
        sendPackets(
            C07PacketPlayerDigging(START_DESTROY_BLOCK, blockPos, enumFacing),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, enumFacing)
        )
    }


     val onRotationUpdate = handler<RotationUpdateEvent> {
            val pos = blockPos ?: return@handler
            val isAirBlock = pos.block == air

            if (isAirBlock || getCenterDistance(pos) > range) {
                blockPos = null
                return@handler
            }

            if (options.rotationsActive) {
                val spot = faceBlock(pos) ?: return@handler

                setTargetRotation(spot.rotation, options = options)
            }
        }


    val onTick = handler<GameTickEvent> {
        blockPos ?: return@handler
        enumFacing ?: return@handler

        if (visualSwing) {
            mc.thePlayer.swingItem()
        } else {
            sendPacket(C0APacketAnimation())
        }

        // Break
        sendPackets(
            C07PacketPlayerDigging(START_DESTROY_BLOCK, blockPos, enumFacing),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, enumFacing)
        )

        mc.playerController.clickBlock(blockPos, enumFacing)
    }


    val onRender3D = handler<Render3DEvent> { 
        drawBlockBox(blockPos ?: return@handler, Color.RED, true)
    }
}