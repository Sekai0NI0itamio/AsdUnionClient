/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.features.module.modules.client.Friends
import net.asd.union.utils.attack.EntityUtils.isLookingOnEntities
import net.asd.union.utils.attack.EntityUtils.isSelected
import net.asd.union.utils.client.ClientUtils.runTimeTicks
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.*
import net.asd.union.utils.rotation.RaycastUtils.raycastEntity
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.isRotationFaced
import net.asd.union.utils.rotation.RotationUtils.isVisible
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.timing.MSTimer
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.potion.Potion
import net.minecraft.util.Vec3

/**
 * Target enumeration, prioritisation, and the "hittable" ray-cast state.
 *
 * Owns [target] / [hittable] / [targetCandidates] / [prevTargetEntities]
 * (the list of entity IDs we've already attacked this cycle) and exposes
 * the same public API the rest of the codebase used to read directly off
 * `KillAura.target` / `KillAura.hittable`.
 *
 * The main `KillAura` module still exposes [target] as a property delegate
 * (so external code like `AuraBridge` keeps working), and [updateHittable]
 * is called by [KillAuraClicker] right before an attack is decided.
 */
internal object KillAuraTargetTracker : MinecraftInstance {

    /** Background cadence for re-scanning world entities. */
    const val BACKGROUND_TARGET_CACHE_INTERVAL = 4

    /** Padding added to the outer reach when collecting candidate entities. */
    const val TARGET_CACHE_PADDING = 1.5

    /** Current combat target. `null` when nothing is selected. */
    var target: EntityLivingBase? = null

    /** True if [target] is in crosshair given the current rotation. */
    var hittable: Boolean = false

    /** Entity IDs we have already targeted this cycle (Switch mode). */
    val prevTargetEntities: MutableList<Int> = mutableListOf()

    /** Cached candidate set built by [refreshTargetCandidates]. */
    val targetCandidates: MutableList<EntityLivingBase> = mutableListOf()

    private var targetCacheTicks = 0
    private val switchTimer = MSTimer()

    /**
     * Main entry point — invoked by `KillAura.update()` (and by the
     * `RotationUpdateEvent` handler). Picks a new target if appropriate.
     */
    fun update() {
        if (KillAuraRequirements.shouldPrioritize()) {
            KillAuraDebug.track("update() → shouldPrioritize, aborting")
            return
        }

        val prev = target
        target = null
        if (prev != null) {
            KillAuraDebug.track("update() → target cleared: ${KillAuraDebug.describeEntity(prev)}")
        }

        // KillAuraTargeter (the separate visual pre-selector module) wins
        // over the in-line search when it's enabled.
        val killAuraTargeter = net.asd.union.features.module.modules.combat.KillAuraTargeter
        if (killAuraTargeter.state) {
            val targeterTarget = killAuraTargeter.getTargetEntity()
            if (targeterTarget != null && targeterTarget.isEntityAlive && isEnemy(targeterTarget)) {
                if (KillAuraRotations.updateRotations(targeterTarget)) {
                    target = targeterTarget
                    KillAuraDebug.track(
                        "update() → KillAuraTargeter pre-selected ${KillAuraDebug.describeEntity(targeterTarget)}"
                    )
                    return
                } else {
                    KillAuraDebug.track("update() → KillAuraTargeter target ${KillAuraDebug.describeEntity(targeterTarget)} rotation update failed")
                }
            } else {
                KillAuraDebug.track("update() → KillAuraTargeter yielded no valid target (targeterTarget=$targeterTarget alive=${targeterTarget?.isEntityAlive} enemy=${targeterTarget?.let(::isEnemy)})")
            }
        }

        if (targetCandidates.isEmpty()) {
            KillAuraDebug.track("update() → candidates empty, forcing refresh")
            refreshTargetCandidates(force = true)
        }

        val switchMode = KillAura.targetMode == "Switch"
        val useBacktrackDistance = Backtrack.handleEvents() && Backtrack.mode == "Legacy"
        KillAuraDebug.track(
            "update() → enumerating candidates: " +
                "size=${targetCandidates.size} switchMode=$switchMode " +
                "backtrack=$useBacktrackDistance prevTargetIds=$prevTargetEntities"
        )

        val thePlayer = mc.thePlayer ?: return

        var bestTarget: EntityLivingBase? = null
        var bestValue: Double? = null

        for (entity in targetCandidates) {
            if (!isEnemy(entity) || switchMode && entity.entityId in prevTargetEntities) {
                KillAuraDebug.track(
                    "update()   skip ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "enemy=${isEnemy(entity)} switchMode=$switchMode " +
                        "inPrev=${entity.entityId in prevTargetEntities}"
                )
                continue
            }

            val distance = if (useBacktrackDistance) {
                Backtrack.runWithNearestTrackedDistance(entity) { thePlayer.getDistanceToEntityBox(entity) }
            } else {
                thePlayer.getDistanceToEntityBox(entity)
            }

            if (switchMode && distance > KillAuraRange.maxRange && prevTargetEntities.isNotEmpty()) {
                KillAuraDebug.track(
                    "update()   skip ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "switch-mode and dist=$distance > maxRange=${KillAuraRange.maxRange} with non-empty prev"
                )
                continue
            }

            val entityFov = rotationDifference(entity)

            if (distance > KillAuraRange.maxRange ||
                KillAura.fov != 180F && entityFov > KillAura.fov
            ) {
                KillAuraDebug.track(
                    "update()   skip ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "dist=$distance > maxRange=${KillAuraRange.maxRange} OR " +
                        "fov=$entityFov > ${KillAura.fov}"
                )
                continue
            }

            if (switchMode && !isLookingOnEntities(entity, KillAura.maxSwitchFOV.toDouble())) {
                KillAuraDebug.track(
                    "update()   skip ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "switch-mode and not within maxSwitchFOV=${KillAura.maxSwitchFOV}"
                )
                continue
            }

            val currentValue = when (KillAura.priority.lowercase()) {
                "distance" -> distance
                "direction" -> entityFov.toDouble()
                "health" -> entity.health.toDouble()
                "livingtime" -> -entity.ticksExisted.toDouble()
                "armor" -> entity.totalArmorValue.toDouble()
                "hurtresistance" -> entity.hurtResistantTime.toDouble()
                "hurttime" -> entity.hurtTime.toDouble()
                "healthabsorption" -> (entity.health + entity.absorptionAmount).toDouble()
                "regenamplifier" -> if (entity.isPotionActive(Potion.regeneration)) {
                    entity.getActivePotionEffect(Potion.regeneration).amplifier.toDouble()
                } else -1.0

                "inweb" -> if (entity.isInWeb) -1.0 else Double.MAX_VALUE
                "onladder" -> if (entity.isOnLadder) -1.0 else Double.MAX_VALUE
                "inliquid" -> if (entity.isInWater || entity.isInLava) -1.0 else Double.MAX_VALUE
                else -> null
            }
            if (currentValue == null) {
                KillAuraDebug.track(
                    "update()   skip ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "priority=${KillAura.priority} yielded null"
                )
                continue
            }

            if (bestValue == null || currentValue < bestValue) {
                bestValue = currentValue
                bestTarget = entity
                KillAuraDebug.track(
                    "update()   new best ${entity.javaClass.simpleName}#${entity.entityId}: " +
                        "priority=${KillAura.priority} value=$currentValue dist=$distance fov=$entityFov"
                )
            }
        }

        if (bestTarget != null) {
            if (Backtrack.runWithNearestTrackedDistance(bestTarget) { KillAuraRotations.updateRotations(bestTarget) }) {
                target = bestTarget
                KillAuraDebug.track(
                    "update() → locked on ${KillAuraDebug.describeEntity(bestTarget)} (priority=${KillAura.priority} value=$bestValue)"
                )
                return
            } else {
                KillAuraDebug.track("update() → bestTarget ${KillAuraDebug.describeEntity(bestTarget)} rotation update failed")
            }
        } else {
            KillAuraDebug.track("update() → no bestTarget from candidate scan")
        }

        if (prevTargetEntities.isNotEmpty()) {
            KillAuraDebug.track("update() → prevTargetEntities non-empty (=$prevTargetEntities), retrying")
            prevTargetEntities.clear()
            update()
        }
    }

    /**
     * Re-build [targetCandidates] from the world's loaded entities. Called
     * every [BACKGROUND_TARGET_CACHE_INTERVAL] ticks while disabled, every
     * tick while enabled.
     */
    fun refreshTargetCandidates(force: Boolean = false) {
        val world = mc.theWorld
        val player = mc.thePlayer

        if (world == null || player == null) {
            KillAuraDebug.track("refreshTargetCandidates → world/player null, clearing")
            targetCandidates.clear()
            targetCacheTicks = 0
            return
        }

        val updateInterval = if (KillAura.state) 1 else BACKGROUND_TARGET_CACHE_INTERVAL

        if (!force) {
            targetCacheTicks++
            if (targetCacheTicks < updateInterval) {
                KillAuraDebug.track(
                    "refreshTargetCandidates → skipping (ticks=$targetCacheTicks < $updateInterval)"
                )
                return
            }
        }

        targetCacheTicks = 0
        val beforeCount = targetCandidates.size
        targetCandidates.clear()

        val useRangeFilter = !Backtrack.handleEvents() || Backtrack.mode != "Legacy"

        val cacheRange = KillAuraRange.maxRange + TARGET_CACHE_PADDING
        val cacheRangeSq = cacheRange * cacheRange
        KillAuraDebug.track(
            "refreshTargetCandidates → cacheRange=$cacheRange (maxRange=${KillAuraRange.maxRange}+" +
                "padding=$TARGET_CACHE_PADDING) useRangeFilter=$useRangeFilter"
        )

        var scanned = 0
        var kept = 0
        for (rawEntity in world.loadedEntityList) {
            scanned++
            if (rawEntity !is EntityLivingBase) continue
            if (rawEntity === player) continue
            if (rawEntity is EntityArmorStand) continue
            if (!rawEntity.isEntityAlive) continue
            if (useRangeFilter && player.getDistanceSqToEntity(rawEntity) > cacheRangeSq) continue
            targetCandidates += rawEntity
            kept++
        }
        KillAuraDebug.track(
            "refreshTargetCandidates → scanned=$scanned kept=$kept (was=$beforeCount) " +
                "force=$force enabled=${KillAura.state} interval=$updateInterval"
        )
    }

    /**
     * `true` if [entity] passes the entity-class / friend / team filters
     * for this KillAura instance.
     */
    fun isEnemy(entity: Entity?): Boolean {
        val selected = isSelected(entity, true)
        val friend = isFriend(entity)
        val enemy = selected && !friend
        if (KillAuraDebug.enabled && entity != null) {
            KillAuraDebug.track(
                "isEnemy(${entity.javaClass.simpleName}#${entity.entityId} " +
                    "'${(entity as? net.minecraft.entity.EntityLivingBase)?.let { (it as? net.minecraft.entity.player.EntityPlayer)?.name ?: it.javaClass.simpleName} }'): " +
                    "isSelected=$selected isFriend=$friend → enemy=$enemy"
            )
        }
        return enemy
    }

    private fun isFriend(entity: Entity?): Boolean =
        entity is EntityPlayer && Friends.isFriend(entity.name)

    /**
     * Refresh [hittable] for the current [target] / [currentRotation].
     * Mirrors the original `updateHittable` method.
     */
    fun updateHittable() {
        val player = mc.thePlayer ?: return
        val eyes = player.eyes
        val rotation = currentRotation ?: player.rotation
        val currentTarget = target ?: return

        if (KillAuraRequirements.shouldPrioritize()) {
            KillAuraDebug.hit("updateHittable → shouldPrioritize, skipping")
            return
        }

        if (!KillAura.options.rotationsActive) {
            val dist = player.getDistanceToEntityBox(currentTarget)
            hittable = dist <= KillAura.range
            KillAuraDebug.hit(
                "updateHittable (no rotations): " +
                    "target=${KillAuraDebug.describeEntity(currentTarget)} " +
                    "dist=$dist range=${KillAura.range} → hittable=$hittable"
            )
            return
        }

        var chosenEntity: Entity? = null

        if (KillAura.raycast) {
            KillAuraDebug.hit(
                "updateHittable (raycast): target=${KillAuraDebug.describeEntity(currentTarget)} " +
                    "range=${KillAura.range} rotation=(${"%.2f".format(rotation.yaw)}, " +
                    "${"%.2f".format(rotation.pitch)}) livingRaycast=${KillAura.livingRaycast} " +
                    "raycastIgnored=${KillAura.raycastIgnored}"
            )
            chosenEntity = raycastEntity(
                KillAura.range.toDouble(), rotation.yaw, rotation.pitch
            ) { entity -> !KillAura.livingRaycast || entity is EntityLivingBase && entity !is EntityArmorStand }

            KillAuraDebug.hit("updateHittable   raycast hit: ${KillAuraDebug.describeEntity(chosenEntity)}")

            if (chosenEntity != null && chosenEntity is EntityLivingBase &&
                !(chosenEntity is EntityPlayer && chosenEntity.isClientFriend())
            ) {
                if (KillAura.raycastIgnored && currentTarget != chosenEntity) {
                    KillAuraDebug.hit(
                        "updateHittable   raycastIgnored: target swapped " +
                            "${KillAuraDebug.describeEntity(currentTarget)} → ${KillAuraDebug.describeEntity(chosenEntity)}"
                    )
                    target = chosenEntity
                }
            }

            hittable = target == chosenEntity
            KillAuraDebug.hit("updateHittable   hittable=$hittable (target==chosenEntity)")
        } else {
            hittable = isRotationFaced(currentTarget, KillAura.range.toDouble(), rotation)
            KillAuraDebug.hit(
                "updateHittable (no-raycast): " +
                    "target=${KillAuraDebug.describeEntity(currentTarget)} " +
                    "range=${KillAura.range} → hittable=$hittable"
            )
        }

        var shouldExcept = false

        chosenEntity ?: target?.run {
            if (ForwardTrack.handleEvents()) {
                ForwardTrack.includeEntityTruePos(this) {
                    checkIfAimingAtBox(this, rotation, eyes, onSuccess = {
                        hittable = true
                        shouldExcept = true
                    })
                }
            }
        }

        if (!hittable || shouldExcept) {
            KillAuraDebug.hit(
                "updateHittable → early return (hittable=$hittable shouldExcept=$shouldExcept)"
            )
            return
        }

        val targetToCheck = chosenEntity ?: target ?: return

        // If player is inside entity, automatic yes because the intercept
        // below cannot check for that. Minecraft does the same, see
        // EntityRenderer line 353.
        if (targetToCheck.hitBox.isVecInside(eyes)) {
            KillAuraDebug.hit(
                "updateHittable → player eyes inside entity box of " +
                    "${KillAuraDebug.describeEntity(targetToCheck)}, automatic hittable=true"
            )
            return
        }

        var checkNormally = true

        if (Backtrack.handleEvents()) {
            Backtrack.loopThroughBacktrackData(targetToCheck) {
                var result = false
                checkIfAimingAtBox(targetToCheck, rotation, eyes, onSuccess = {
                    checkNormally = false
                    result = true
                }, onFail = {
                    result = false
                })
                return@loopThroughBacktrackData result
            }
        } else if (ForwardTrack.handleEvents()) {
            ForwardTrack.includeEntityTruePos(targetToCheck) {
                checkIfAimingAtBox(targetToCheck, rotation, eyes, onSuccess = { checkNormally = false })
            }
        }

        if (!checkNormally) {
            KillAuraDebug.hit("updateHittable → Backtrack/ForwardTrack took over, checkNormally=false")
            return
        }

        // Recreate raycast logic
        val intercept = targetToCheck.hitBox.calculateIntercept(
            eyes, eyes + getVectorForRotation(rotation) * KillAura.range.toDouble()
        )
        val distToTarget = player.getDistanceToEntityBox(targetToCheck)

        // Is the entity box raycast vector visible? If not, check through-wall range
        val visible = isVisible(intercept.hitVec)
        val withinThroughWalls = distToTarget <= KillAura.throughWallsRange
        hittable = visible || withinThroughWalls
        KillAuraDebug.hit(
            "updateHittable → final raycast: " +
                "target=${KillAuraDebug.describeEntity(targetToCheck)} " +
                "intercept=${KillAuraDebug.describeVec(intercept.hitVec)} " +
                "visible=$visible dist=$distToTarget throughWallsRange=${KillAura.throughWallsRange} " +
                "withinThroughWalls=$withinThroughWalls → hittable=$hittable"
        )
    }

    /**
     * Helper used by [updateHittable] and `Backtrack` / `ForwardTrack` to
     * check the player's aim against a given entity's bounding box.
     */
    fun checkIfAimingAtBox(
        targetToCheck: Entity, rotation: Rotation, eyes: Vec3,
        onSuccess: () -> Unit, onFail: () -> Unit = { },
    ) {
        if (targetToCheck.hitBox.isVecInside(eyes)) {
            KillAuraDebug.hit("checkIfAimingAtBox: player inside box of ${KillAuraDebug.describeEntity(targetToCheck)}")
            onSuccess()
            return
        }

        // Recreate raycast logic
        val intercept = targetToCheck.hitBox.calculateIntercept(
            eyes, eyes + getVectorForRotation(rotation) * KillAura.range.toDouble()
        )

        if (intercept != null) {
            // Is the entity box raycast vector visible? If not, check through-wall range
            val visible = isVisible(intercept.hitVec)
            val withinThroughWalls = mc.thePlayer.getDistanceToEntityBox(targetToCheck) <= KillAura.throughWallsRange
            hittable = visible || withinThroughWalls

            KillAuraDebug.hit(
                "checkIfAimingAtBox: target=${KillAuraDebug.describeEntity(targetToCheck)} " +
                    "intercept=${KillAuraDebug.describeVec(intercept.hitVec)} " +
                    "visible=$visible withinThroughWalls=$withinThroughWalls → hittable=$hittable"
            )

            if (hittable) {
                onSuccess()
                return
            }
        } else {
            KillAuraDebug.hit(
                "checkIfAimingAtBox: target=${KillAuraDebug.describeEntity(targetToCheck)} intercept=null"
            )
        }

        onFail()
    }

    /**
     * Reset transient state. Called by `KillAura.onToggle(false)` and
     * `onWorldChange`.
     */
    fun reset() {
        KillAuraDebug.evt("KillAuraTargetTracker.reset(): clearing target/candidates/prevTargets")
        target = null
        hittable = false
        prevTargetEntities.clear()
        targetCandidates.clear()
        targetCacheTicks = 0
        switchTimer.reset()
    }
}
