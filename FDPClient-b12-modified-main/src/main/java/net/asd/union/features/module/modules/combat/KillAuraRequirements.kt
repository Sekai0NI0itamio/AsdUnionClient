/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.FDPClient
import net.asd.union.features.module.modules.client.Friends
import net.asd.union.features.module.modules.other.Fucker
import net.asd.union.features.module.modules.other.Nuker
import net.asd.union.features.module.modules.player.scaffolds.Scaffold
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.*
import net.asd.union.utils.inventory.ItemUtils.isConsumingItem
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion
import net.minecraft.item.ItemSword

/**
 * Gating checks for [KillAura]: decides whether the module should run, and
 * exposes the small helpers (alive check, prioritize, cancel-run) that the
 * rest of the sub-objects call into.
 *
 * Mirrors `KillAuraRequirements` in LiquidBounce.
 */
internal object KillAuraRequirements : MinecraftInstance {

    /** Last time the inventory was opened. Used by `noInventoryDelay`. */
    var containerOpen = -1L

    /**
     * True if [KillAura] should yield to another module. Mirrors the old
     * `shouldPrioritize()` method in the original monolithic `KillAura.kt`.
     */
    fun shouldPrioritize(): Boolean {
        val scaffoldActive = !KillAura.onScaffold && Scaffold.handleEvents() &&
            (Scaffold.placeRotation != null || currentRotation != null)
        if (scaffoldActive) {
            KillAuraDebug.gate(
                "shouldPrioritize=true: onScaffold=${KillAura.onScaffold} " +
                    "Scaffold.handleEvents=${Scaffold.handleEvents()} " +
                    "placeRotation=${Scaffold.placeRotation != null} " +
                    "currentRotation=${currentRotation != null}"
            )
            return true
        }

        val blockActive = !KillAura.onDestroyBlock &&
            (Fucker.handleEvents() && !Fucker.noHit && Fucker.pos != null ||
                Nuker.handleEvents())
        if (blockActive) {
            KillAuraDebug.gate(
                "shouldPrioritize=true: onDestroyBlock=${KillAura.onDestroyBlock} " +
                    "Fucker=${Fucker.handleEvents()}/${!Fucker.noHit}/${Fucker.pos != null} " +
                    "Nuker=${Nuker.handleEvents()}"
            )
            return true
        }

        val slotMismatch = KillAura.activationSlot && SilentHotbar.currentSlot != KillAura.preferredSlot - 1
        if (slotMismatch) {
            KillAuraDebug.gate(
                "shouldPrioritize=true: activationSlot=${KillAura.activationSlot} " +
                    "currentSlot=$SilentHotbar.currentSlot " +
                    "preferredSlot=${KillAura.preferredSlot}"
            )
            return true
        }

        KillAuraDebug.gate("shouldPrioritize=false")
        return false
    }

    /**
     * True if this tick should not run KillAura at all (spectator, dead,
     * consuming, blinking, etc.). The original `cancelRun` is now a
     * function on the main `KillAura` object that delegates here.
     */
    val cancelRun: Boolean
        get() {
            val player = mc.thePlayer ?: run {
                KillAuraDebug.gate("cancelRun=true: no player")
                return true
            }

            if (player.isSpectator) {
                KillAuraDebug.gate("cancelRun=true: spectator")
                return true
            }
            if (!isAlive(player)) {
                KillAuraDebug.gate("cancelRun=true: not alive (health=${player.health} entityAlive=${player.isEntityAlive})")
                return true
            }
            if (KillAura.noConsumeAttack == "NoRotation" && isConsumingItem()) {
                KillAuraDebug.gate("cancelRun=true: noConsumeAttack=NoRotation and isConsumingItem")
                return true
            }
            if (shouldCancelDueToModuleState()) {
                KillAuraDebug.gate("cancelRun=true: shouldCancelDueToModuleState")
                return true
            }
            if (isEatingDisallowed()) {
                KillAuraDebug.gate("cancelRun=true: isEatingDisallowed (noEat=${KillAura.noEat})")
                return true
            }
            if (isBlockingDisallowed()) {
                KillAuraDebug.gate("cancelRun=true: isBlockingDisallowed (noBlocking=${KillAura.noBlocking})")
                return true
            }

            KillAuraDebug.gate("cancelRun=false")
            return false
        }

    private fun shouldCancelDueToModuleState(): Boolean {
        if (KillAura.blinkCheck && FDPClient.moduleManager["Blink"]?.state == true) return true
        if (KillAura.noScaffold && FDPClient.moduleManager[Scaffold::class.java.simpleName]?.state == true) return true
        if (KillAura.noFly && FDPClient.moduleManager["Flight"]?.state == true) return true
        if (KillAura.onSwording && mc.thePlayer.heldItem?.item !is ItemSword) return true
        return false
    }

    private fun isEatingDisallowed(): Boolean {
        if (!KillAura.noEat) return false
        val held = mc.thePlayer.heldItem?.item ?: return false
        return mc.thePlayer.isUsingItem && (held is ItemFood || held is ItemBucketMilk || held is ItemPotion)
    }

    private fun isBlockingDisallowed(): Boolean {
        if (!KillAura.noBlocking) return false
        return mc.thePlayer.isUsingItem && mc.thePlayer.heldItem?.item is ItemBlock
    }

    /**
     * True if [entity] is alive. Matches the original `isAlive()` helper.
     */
    fun isAlive(entity: EntityLivingBase): Boolean {
        val alive = entity.isEntityAlive && entity.health > 0
        if (KillAuraDebug.enabled) {
            KillAuraDebug.gate(
                "isAlive(${entity.javaClass.simpleName}#${entity.entityId}): " +
                    "isEntityAlive=${entity.isEntityAlive} health=${entity.health} → $alive"
            )
        }
        return alive
    }

    /**
     * Reset the container-open timer. Called by [KillAura.onToggle] /
     * [KillAura.onWorldChange] so a freshly-enabled aura doesn't see a stale
     * timestamp.
     */
    fun reset() {
        containerOpen = -1L
    }
}
