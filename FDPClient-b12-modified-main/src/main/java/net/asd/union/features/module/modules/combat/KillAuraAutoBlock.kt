/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.utils.attack.CPSCounter
import net.asd.union.utils.client.BlinkUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.*
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.kotlin.RandomUtils.nextInt
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.minecraft.entity.Entity
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action.INTERACT
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing

/**
 * Auto-block state and behaviour for [KillAura]. Owns [renderBlocking] /
 * [blockStatus] / [blockStopInDead] / [blinked] and exposes the same
 * public API the rest of the codebase used to read directly off
 * `KillAura.renderBlocking` / `KillAura.blockStatus`.
 *
 * Mirrors `KillAuraAutoBlock` in LiquidBounce. The 1.8.9 implementation is
 * smaller because it doesn't have to deal with 1.9+ shield packets or the
 * 1.21.4 sword-block animation; the interaction-mode logic is the same
 * (sneak-right-click on the target's bounding box).
 */
internal object KillAuraAutoBlock : MinecraftInstance {

    /** True if the client should render the blocking animation. */
    var renderBlocking: Boolean = false

    /** True if the server believes we are blocking (we sent a useItem). */
    var blockStatus: Boolean = false

    /** Set when we lost our target while blocking — used by the death-stop path. */
    var blockStopInDead: Boolean = false

    /** Used by blink auto-block: true if a blink has been opened. */
    var blinked: Boolean = false

    /** True if [BlinkUtils] is currently queueing packets for us. */
    val blinking: Boolean
        get() = BlinkUtils.isBlinking

    /**
     * Convenience: is the player holding a blockable item (sword) and is
     * the target within the block range? Mirrors the original `canBlock`.
     */
    val canBlock: Boolean
        get() {
            val player = mc.thePlayer ?: return false
            val target = KillAuraTargetTracker.target ?: return false

            if (player.heldItem?.item !is ItemSword) {
                KillAuraDebug.blk(
                    "canBlock=false: heldItem=${player.heldItem?.item?.javaClass?.simpleName} " +
                        "(not an ItemSword)"
                )
                return false
            }

            if (KillAura.smartAutoBlock) {
                if (player.isMoving && KillAura.forceBlock) {
                    KillAuraDebug.blk("canBlock=false: smartAutoBlock && isMoving && forceBlock")
                    return false
                }
                if (KillAura.checkWeapon &&
                    target.heldItem?.item !is ItemSword &&
                    target.heldItem?.item !is ItemAxe
                ) {
                    KillAuraDebug.blk(
                        "canBlock=false: smartAutoBlock + checkWeapon + " +
                            "target.heldItem=${target.heldItem?.item?.javaClass?.simpleName}"
                    )
                    return false
                }
                if (player.hurtTime > KillAura.maxOwnHurtTime) {
                    KillAuraDebug.blk(
                        "canBlock=false: smartAutoBlock + ownHurtTime=${player.hurtTime} > " +
                            "maxOwnHurtTime=${KillAura.maxOwnHurtTime}"
                    )
                    return false
                }

                val rotationToPlayer = toRotation(player.hitBox.center, true, target)
                val dirDiff = rotationDifference(rotationToPlayer, target.rotation)
                if (dirDiff > KillAura.maxDirectionDiff) {
                    KillAuraDebug.blk(
                        "canBlock=false: smartAutoBlock + directionDiff=$dirDiff > " +
                            "maxDirectionDiff=${KillAura.maxDirectionDiff}"
                    )
                    return false
                }
                if (target.swingProgressInt > KillAura.maxSwingProgress) {
                    KillAuraDebug.blk(
                        "canBlock=false: smartAutoBlock + target.swingProgressInt=${target.swingProgressInt} > " +
                            "maxSwingProgress=${KillAura.maxSwingProgress}"
                    )
                    return false
                }
                val dist = target.getDistanceToEntityBox(player)
                if (dist > KillAura.blockRange) {
                    KillAuraDebug.blk(
                        "canBlock=false: smartAutoBlock + dist=$dist > blockRange=${KillAura.blockRange}"
                    )
                    return false
                }
            }

            val dist = player.getDistanceToEntityBox(target)
            if (dist > KillAura.blockMaxRange) {
                KillAuraDebug.blk(
                    "canBlock=false: dist=$dist > blockMaxRange=${KillAura.blockMaxRange} " +
                        "for target ${KillAuraDebug.describeEntity(target)}"
                )
                return false
            }
            KillAuraDebug.blk(
                "canBlock=true: heldItem=Sword target=${KillAuraDebug.describeEntity(target)} dist=$dist " +
                    "(smartAutoBlock=${KillAura.smartAutoBlock} blockMaxRange=${KillAura.blockMaxRange})"
            )
            return true
        }

    /**
     * Send the useItem packet (or two C02 use-entity packets when in
     * "interact" mode). Mirrors the original `startBlocking`.
     */
    fun startBlocking(interactEntity: Entity, interact: Boolean, fake: Boolean = false) {
        val player = mc.thePlayer ?: return

        if (blockStatus && (!KillAura.uncpAutoBlock || !KillAura.blinkAutoBlock) ||
            KillAuraRequirements.shouldPrioritize()
        ) {
            KillAuraDebug.blk(
                "startBlocking → skipping: blockStatus=$blockStatus " +
                    "uncpAutoBlock=${KillAura.uncpAutoBlock} blinkAutoBlock=${KillAura.blinkAutoBlock} " +
                    "shouldPrioritize=${KillAuraRequirements.shouldPrioritize()}"
            )
            return
        }

        if (player.isBlocking) {
            KillAuraDebug.blk(
                "startBlocking → player already blocking, setting " +
                    "blockStatus=true renderBlocking=true (no packet sent)"
            )
            blockStatus = true
            renderBlocking = true
            return
        }

        if (!fake) {
            val roll = nextInt(endExclusive = 100)
            if (!(KillAura.blockRate > 0 && roll <= KillAura.blockRate)) {
                KillAuraDebug.blk(
                    "startBlocking → blockRate gate failed: roll=$roll > " +
                        "blockRate=${KillAura.blockRate}, abort"
                )
                return
            }

            if (interact) {
                val positionEye = player.eyes
                val boundingBox = interactEntity.hitBox
                val (yaw, pitch) = currentRotation ?: player.rotation
                val vec = getVectorForRotation(Rotation(yaw, pitch))
                val lookAt = positionEye.add(vec * KillAuraRange.maxRange.toDouble())
                val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: run {
                    KillAuraDebug.blk("startBlocking → interact=ON but calculateIntercept returned null")
                    return
                }
                val hitVec = movingObject.hitVec

                KillAuraDebug.pkt(
                    "startBlocking → SENDING 2x C02PacketUseEntity(INTERACT) to " +
                        "${KillAuraDebug.describeEntity(interactEntity)} (hitVec=${KillAuraDebug.describeVec(hitVec)})"
                )
                sendPackets(
                    C02PacketUseEntity(interactEntity, hitVec - interactEntity.positionVector),
                    C02PacketUseEntity(interactEntity, INTERACT)
                )
            }

            if (KillAura.switchStartBlock) {
                KillAuraDebug.blk("startBlocking → switchStartBlock=true, slot switch")
                switchToSlot((SilentHotbar.currentSlot + 1) % 9)
            }

            KillAuraDebug.pkt(
                "startBlocking → SENDING C08PacketPlayerBlockPlacement " +
                    "(heldItem=${player.heldItem?.item?.javaClass?.simpleName})"
            )
            sendPacket(C08PacketPlayerBlockPlacement(player.heldItem))
            blockStatus = true
        } else {
            KillAuraDebug.blk("startBlocking → fake=true, not sending real useItem packet")
        }

        renderBlocking = true
        CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
    }

    /**
     * Send RELEASE_USE_ITEM / slot-switch packets to leave the blocking
     * state. The exact mode depends on [KillAura.unblockMode].
     */
    fun stopBlocking(forceStop: Boolean = false) {
        val player = mc.thePlayer ?: return

        if (!forceStop) {
            if (blockStatus && !player.isBlocking) {
                when (KillAura.unblockMode.lowercase()) {
                    "stop" -> {
                        KillAuraDebug.pkt(
                            "stopBlocking → SENDING C07PacketPlayerDigging(RELEASE_USE_ITEM) " +
                                "(unblockMode=${KillAura.unblockMode})"
                        )
                        sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    }
                    "switch" -> {
                        KillAuraDebug.blk("stopBlocking → unblockMode=switch, slot switch")
                        switchToSlot((SilentHotbar.currentSlot + 1) % 9)
                    }
                    "empty" -> {
                        KillAuraDebug.blk("stopBlocking → unblockMode=empty, slot switch to firstEmptyStack")
                        switchToSlot(player.inventory.firstEmptyStack)
                    }
                }
                blockStatus = false
            } else {
                KillAuraDebug.blk(
                    "stopBlocking → not sending release: blockStatus=$blockStatus " +
                        "player.isBlocking=${player.isBlocking}"
                )
            }
        } else {
            if (blockStatus) {
                KillAuraDebug.pkt(
                    "stopBlocking(force) → SENDING C07PacketPlayerDigging(RELEASE_USE_ITEM) " +
                        "(forced release)"
                )
                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            } else {
                KillAuraDebug.blk("stopBlocking(force) → blockStatus=false, no packet sent")
            }
            blockStatus = false
        }

        renderBlocking = false
    }

    /**
     * Drive the blink auto-block state machine. Called from the main
     * `KillAura.onTick` handler — opens / closes the blink queue so the
     * server never sees the transient unblock.
     */
    fun tickBlinkAutoBlock() {
        val player = mc.thePlayer ?: return

        val phase = player.ticksExisted % (KillAura.blinkBlockTicks + 1)
        when (phase) {
            0 -> {
                if (blockStatus && !blinked && !BlinkUtils.isBlinking) {
                    KillAuraDebug.blk("tickBlinkAutoBlock: phase=0 → opening blink (blockStatus=$blockStatus)")
                    blinked = true
                } else {
                    KillAuraDebug.blk(
                        "tickBlinkAutoBlock: phase=0 no-op " +
                            "(blockStatus=$blockStatus blinked=$blinked isBlinking=${BlinkUtils.isBlinking})"
                    )
                }
            }
            1 -> {
                if (blockStatus && blinked && BlinkUtils.isBlinking) {
                    KillAuraDebug.blk("tickBlinkAutoBlock: phase=1 → stopBlocking() while blink is open")
                    stopBlocking()
                } else {
                    KillAuraDebug.blk(
                        "tickBlinkAutoBlock: phase=1 no-op " +
                            "(blockStatus=$blockStatus blinked=$blinked isBlinking=${BlinkUtils.isBlinking})"
                    )
                }
            }
            KillAura.blinkBlockTicks -> {
                if (!blockStatus && blinked && BlinkUtils.isBlinking) {
                    KillAuraDebug.blk("tickBlinkAutoBlock: phase=$phase → unblink + startBlocking()")
                    BlinkUtils.unblink()
                    blinked = false
                    val target = KillAuraTargetTracker.target ?: return
                    startBlocking(target, KillAura.interactAutoBlock, KillAura.autoBlock == "Fake")
                } else {
                    KillAuraDebug.blk(
                        "tickBlinkAutoBlock: phase=$phase no-op " +
                            "(blockStatus=$blockStatus blinked=$blinked isBlinking=${BlinkUtils.isBlinking})"
                    )
                }
            }
        }
    }

    /**
     * Reset transient state. Used by `KillAura.onToggle(false)` and
     * `onWorldChange`.
     */
    fun reset() {
        KillAuraDebug.evt(
            "KillAuraAutoBlock.reset(): renderBlocking=$renderBlocking blockStatus=$blockStatus " +
                "blockStopInDead=$blockStopInDead blinked=$blinked"
        )
        renderBlocking = false
        blockStatus = false
        blockStopInDead = false
        blinked = false
        if (KillAura.blinkAutoBlock) {
            if (BlinkUtils.isBlinking) {
                KillAuraDebug.blk("reset: blinkAutoBlock=ON and isBlinking=true, unblinking")
                BlinkUtils.unblink()
            }
            blinked = false
        }
    }

    private fun switchToSlot(slot: Int) {
        SilentHotbar.selectSlotSilently(KillAura, slot, immediate = true)
        SilentHotbar.resetSlot(KillAura, true)
    }
}
