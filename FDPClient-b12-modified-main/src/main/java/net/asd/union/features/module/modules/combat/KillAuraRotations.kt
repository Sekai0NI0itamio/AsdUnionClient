/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.*
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.rotation.RotationUtils.modifiedInput
import net.asd.union.utils.rotation.RotationUtils.searchCenter
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.simulation.SimulatedPlayer
import net.minecraft.entity.Entity

/**
 * Rotation update logic for [KillAura]. Owns the body-point / search-config
 * delegation previously inlined in `KillAura.updateRotations()`.
 *
 * Mirrors the rotation half of LiquidBounce's
 * `net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRotationsValueGroup`
 * — but adapted to FDPClient's existing `RotationSettings` / `SimulatedPlayer`
 * primitives, so the actual server-side rotation engine is unchanged.
 */
internal object KillAuraRotations : MinecraftInstance {

    /**
     * Compute a rotation that aims at [entity] and commit it to
     * `RotationUtils.currentRotation`. Returns `true` if a valid rotation
     * was found, `false` otherwise.
     *
     * Mirrors the original `updateRotations()` in monolithic `KillAura.kt`.
     */
    fun updateRotations(entity: Entity): Boolean {
        val player = mc.thePlayer ?: return false

        if (KillAuraRequirements.shouldPrioritize()) {
            KillAuraDebug.rot("updateRotations → shouldPrioritize, returning false")
            return false
        }

        if (!KillAura.options.rotationsActive) {
            val dist = player.getDistanceToEntityBox(entity)
            val ok = dist <= KillAura.range
            KillAuraDebug.rot(
                "updateRotations (no rotations) entity=${KillAuraDebug.describeEntity(entity)} " +
                    "dist=$dist range=${KillAura.range} → $ok"
            )
            return ok
        }

        val prediction = entity.currPos.subtract(entity.prevPos)
            .times(2 + KillAura.predictEnemyPosition.toDouble())
        val boundingBox = entity.hitBox.offset(prediction)
        val (currPos, oldPos) = player.currPos to player.prevPos

        KillAuraDebug.rot(
            "updateRotations start entity=${KillAuraDebug.describeEntity(entity)} " +
                "predictEnemyPos=${KillAura.predictEnemyPosition} predictClientMove=${KillAura.predictClientMovement} " +
                "predictOnlyWhenOutOfRange=${KillAura.predictOnlyWhenOutOfRange} " +
                "currPos=${KillAuraDebug.describeVec(currPos)} prevPos=${KillAuraDebug.describeVec(oldPos)}"
        )

        val simPlayer = SimulatedPlayer.fromClientPlayer(modifiedInput)
        simPlayer.rotationYaw = (currentRotation ?: player.rotation).yaw

        var pos = currPos

        repeat(KillAura.predictClientMovement) {
            val previousPos = simPlayer.pos

            simPlayer.tick()

            if (KillAura.predictOnlyWhenOutOfRange) {
                player.setPosAndPrevPos(simPlayer.pos)

                val currDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(previousPos)

                val prevDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(currPos, oldPos)
                pos = simPlayer.pos

                if (currDist <= KillAura.range && currDist <= prevDist) {
                    return@repeat
                }
            }

            pos = previousPos
        }

        player.setPosAndPrevPos(pos)

        val rotation = searchCenter(
            boundingBox,
            KillAura.generateSpotBasedOnDistance,
            KillAura.outborder && !KillAuraClicker.attackTimer.hasTimePassed(KillAuraClicker.attackDelay / 2),
            KillAura.randomization,
            predict = false,
            lookRange = KillAura.range + KillAura.scanRange,
            attackRange = KillAura.range,
            throughWallsRange = KillAura.throughWallsRange,
            bodyPoints = listOf(KillAura.highestBodyPointToTarget, KillAura.lowestBodyPointToTarget),
            horizontalSearch = KillAura.minHorizontalBodySearch.get()..KillAura.maxHorizontalBodySearch.get()
        )

        if (rotation == null) {
            player.setPosAndPrevPos(currPos, oldPos)
            KillAuraDebug.rot(
                "updateRotations → searchCenter returned null for ${KillAuraDebug.describeEntity(entity)} " +
                    "bodyPoints=[${KillAura.highestBodyPointToTarget}, ${KillAura.lowestBodyPointToTarget}] " +
                    "lookRange=${KillAura.range + KillAura.scanRange} " +
                    "attackRange=${KillAura.range} throughWallsRange=${KillAura.throughWallsRange}"
            )
            return false
        }

        setTargetRotation(rotation, options = KillAura.options)
        KillAuraDebug.rot(
            "updateRotations → committed rotation " +
                "(${KillAuraDebug.format(rotation.yaw)}, ${KillAuraDebug.format(rotation.pitch)}) " +
                "for ${KillAuraDebug.describeEntity(entity)}"
        )

        player.setPosAndPrevPos(currPos, oldPos)

        return true
    }
}
