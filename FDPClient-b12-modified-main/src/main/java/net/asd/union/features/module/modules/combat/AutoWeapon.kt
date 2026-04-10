/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.boolean
import net.asd.union.config.int
import net.asd.union.event.AttackEvent
import net.asd.union.event.PacketEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.inventory.attackDamage
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK

object AutoWeapon : Module("AutoWeapon", Category.COMBAT, subjective = true, hideModule = false) {

    private val onlySword by boolean("OnlySword", false)

    private val spoof by boolean("SpoofItem", false)
    private val spoofTicks by int("SpoofTicks", 10, 1..20) { spoof }

    private var attackEnemy = false

    val onAttack = handler<AttackEvent> {
        attackEnemy = true
    }

    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (event.packet is C02PacketUseEntity && event.packet.action == ATTACK && attackEnemy) {
            attackEnemy = false

            // Find the best weapon in hotbar (#Kotlin Style)
            val (slot, _) = (0..8)
                .map { it to mc.thePlayer.inventory.getStackInSlot(it) }
                .filter {
                    it.second != null && ((onlySword && it.second.item is ItemSword)
                            || (!onlySword && (it.second.item is ItemSword || it.second.item is ItemTool)))
                }
                .maxByOrNull { it.second.attackDamage } ?: return@handler

            if (slot == mc.thePlayer.inventory.currentItem) // If in hand no need to swap
                return@handler

            // Switch to best weapon
            SilentHotbar.selectSlotSilently(this, slot, spoofTicks, true, !spoof, spoof)

            if (!spoof) {
                player.inventory.currentItem = slot
                SilentHotbar.resetSlot(this)
            }

            // Resend attack packet
            sendPacket(event.packet)
            event.cancelEvent()
        }
    }
}