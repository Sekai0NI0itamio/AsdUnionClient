/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.int
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.extensions.rotation
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.minecraft.item.ItemBow
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

object FastBow : Module("FastBow", Category.COMBAT, hideModule = false) {

    private val packets by int("Packets", 20, 3..20)

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (!thePlayer.isUsingItem)
            return@handler

        val currentItem = thePlayer.inventory.getCurrentItem()

        if (currentItem != null && currentItem.item is ItemBow) {
            sendPacket(
                C08PacketPlayerBlockPlacement(
                    BlockPos.ORIGIN,
                    255,
                    mc.thePlayer.currentEquippedItem,
                    0F,
                    0F,
                    0F
                )
            )

            val (yaw, pitch) = currentRotation ?: thePlayer.rotation

            repeat(packets) {
                sendPacket(C05PacketPlayerLook(yaw, pitch, true))
            }

            sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            thePlayer.itemInUseCount = currentItem.maxItemUseDuration - 1
        }
    }
}