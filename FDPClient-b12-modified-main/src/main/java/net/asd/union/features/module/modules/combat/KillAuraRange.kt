/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.minecraft.entity.Entity
import kotlin.math.max

/**
 * Range configuration and per-entity reach calculation.
 *
 * Ported out of `KillAura.kt` so the range math lives in one place. Reads
 * settings from [KillAura] (range / scanRange / throughWallsRange /
 * rangeSprintReduction) so user configs remain compatible.
 *
 * Mirrors `KillAuraRange` in LiquidBounce's
 * `net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features`
 * package, but inlined into the 1.8.9 codebase.
 */
internal object KillAuraRange : MinecraftInstance {

    /**
     * Outer reach used when collecting candidate entities: max(attack,
     * throughWalls) — i.e. the larger of the two so we never filter out a
     * candidate that the user could legally hit.
     */
    val maxRange: Float
        get() {
            val attackPlusScan = KillAura.range + KillAura.scanRange
            val throughWalls = KillAura.throughWallsRange
            val result = max(attackPlusScan, throughWalls)
            KillAuraDebug.rng(
                "maxRange: max(range+scanRange=$attackPlusScan, throughWallsRange=$throughWalls) = $result"
            )
            return result
        }

    /**
     * Per-entity reach. If the target is beyond [KillAura.throughWallsRange]
     * we use `range + scanRange`, otherwise we use `throughWallsRange` (the
     * "creative reach" path). Sprint reduces reach by
     * [KillAura.rangeSprintReduction].
     */
    fun getRange(entity: Entity): Float {
        val player = mc.thePlayer ?: return KillAura.range
        val dist = player.getDistanceToEntityBox(entity)
        val base = if (dist >= KillAura.throughWallsRange) {
            KillAura.range + KillAura.scanRange
        } else {
            KillAura.throughWallsRange
        }
        val sprint = if (player.isSprinting) KillAura.rangeSprintReduction else 0f
        val result = base - sprint
        KillAuraDebug.rng(
            "getRange(${entity.javaClass.simpleName}#${entity.entityId}): " +
                "dist=$dist range=${KillAura.range} scanRange=${KillAura.scanRange} " +
                "throughWallsRange=${KillAura.throughWallsRange} " +
                "sprinting=${player.isSprinting} sprintReduction=$sprint → base=$base reach=$result"
        )
        return result
    }
}
