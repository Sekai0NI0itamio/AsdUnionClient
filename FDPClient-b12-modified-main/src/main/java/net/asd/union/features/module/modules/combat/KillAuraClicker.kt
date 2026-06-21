/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.FDPClient
import net.asd.union.utils.attack.CPSCounter
import net.asd.union.utils.attack.CooldownHelper.resetLastAttackedTicks
import net.asd.union.utils.attack.EntityUtils.isSelected
import net.asd.union.utils.client.ClientUtils.runTimeTicks
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.extensions.*
import net.asd.union.utils.inventory.InventoryUtils.serverOpenInventory
import net.asd.union.utils.inventory.ItemUtils.isConsumingItem
import net.asd.union.utils.kotlin.RandomUtils.nextInt
import net.asd.union.utils.render.ColorSettingsInteger
import net.asd.union.utils.render.RenderUtils
import net.asd.union.utils.rotation.RaycastUtils.runWithModifiedRaycastResult
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.timing.MSTimer
import net.asd.union.utils.timing.TickedActions.nextTick
import net.asd.union.utils.timing.TimeUtils.randomClickDelay
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import kotlin.math.roundToInt

/**
 * Attack pipeline: timing (`attackTimer` / `attackDelay` / `clicks`),
 * miss-aware click rules, fail-swing rendering, and the `attackEntity`
 * call that actually delivers the hit.
 *
 * The legacy `runAttack(...)` method on monolithic `KillAura.kt` was
 * 175 lines long. Splitting it out keeps `KillAura.onTick` readable and
 * makes the click rules unit-testable.
 */
internal object KillAuraClicker : MinecraftInstance {

    /** Time since last scheduled attack. */
    val attackTimer: MSTimer = MSTimer()

    /** Current scheduled delay in ms. Recomputed on each click. */
    var attackDelay: Int = 0

    /** Click budget for the current tick (consumed by `runAttack`). */
    var clicks: Int = 0

    /** Last attack info, used by `shouldDelayClick` and the rotation-difference gate. */
    var lastAttackTickData: Pair<MovingObjectPosition, Int>? = null

    /** Pending "swing fail" boxes rendered by [handleFailedSwings]. */
    val swingFails: MutableList<SwingFailData> = mutableListOf()

    /** Time (in ticks) since the last click. */
    fun ticksSinceClick(): Int =
        runTimeTicks - (lastAttackTickData?.second ?: 0)

    /**
     * Should the next click be delayed because the raycast object type
     * changed? (Vanilla imposes a 1-tick cool-down on type change; we
     * extend that to [KillAura.hitDelayTicks].)
     */
    fun shouldDelayClick(currentType: MovingObjectPosition.MovingObjectType): Boolean {
        if (!KillAura.useHitDelay) {
            return false
        }

        val lastAttack = lastAttackTickData

        val delay = lastAttack != null &&
            lastAttack.first.typeOfHit != currentType &&
            runTimeTicks - lastAttack.second <= KillAura.hitDelayTicks
        if (KillAuraDebug.enabled) {
            KillAuraDebug.clk(
                "shouldDelayClick($currentType) → useHitDelay=${KillAura.useHitDelay} " +
                    "last=${lastAttack?.first?.typeOfHit} " +
                    "ticksSince=${lastAttack?.second?.let { runTimeTicks - it }} " +
                    "hitDelayTicks=${KillAura.hitDelayTicks} → $delay"
            )
        }
        return delay
    }

    /**
     * Schedule a single click — call this from `onRender3D` to give the
     * attack timer work. Mirrors the inline block in the original
     * `onRender3D` handler.
     */
    fun onRenderTick() {
        val target = KillAuraTargetTracker.target ?: return

        if (attackTimer.hasTimePassed(attackDelay)) {
            val newDelay = randomClickDelay(KillAura.minCPS, KillAura.maxCPS)
            if (KillAura.maxCPS > 0) {
                clicks++
                KillAuraDebug.clk(
                    "onRenderTick: timer ready, scheduling click → " +
                        "clicks=$clicks attackDelay=$attackDelay→$newDelay (CPS=${KillAura.minCPS}..${KillAura.maxCPS}) " +
                        "for target ${KillAuraDebug.describeEntity(target)}"
                )
            } else {
                KillAuraDebug.clk("onRenderTick: timer ready, but maxCPS=0 → no click scheduled")
            }
            attackTimer.reset()
            attackDelay = newDelay
        } else {
            KillAuraDebug.clk(
                "onRenderTick: timer not ready, remaining=${attackDelay - attackTimer.time}ms " +
                    "target=${KillAuraDebug.describeEntity(target)}"
            )
        }
    }

    /**
     * Run one attack pass. Consumes one click from the [clicks] budget.
     * `isFirstClick` / `isLastClick` distinguish first/last clicks inside
     * a multi-click "butterfly" tick.
     */
    fun runAttack(isFirstClick: Boolean, isLastClick: Boolean) {
        val initialTarget = KillAuraTargetTracker.target ?: run {
            KillAuraDebug.evt("runAttack → no target, abort")
            return
        }
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        KillAuraDebug.evt(
            "runAttack start: target=${KillAuraDebug.describeEntity(initialTarget)} " +
                "first=$isFirstClick last=$isLastClick clicks=${clicks}"
        )

        if (KillAura.noConsumeAttack == "NoHits" && isConsumingItem()) {
            KillAuraDebug.gate("runAttack → noConsumeAttack=NoHits and isConsumingItem, abort")
            return
        }

        // Settings
        val multi = KillAura.targetMode == "Multi"
        val manipulateInventory = KillAura.simulateClosingInventory &&
            !KillAura.noInventoryAttack && serverOpenInventory

        KillAuraTargetTracker.updateHittable()

        // `updateHittable` may have swapped `target` (raycast-ignored mode)
        val currentTarget = KillAuraTargetTracker.target ?: run {
            KillAuraDebug.evt("runAttack → target nulled by updateHittable, abort")
            return
        }
        if (currentTarget != initialTarget) {
            KillAuraDebug.evt(
                "runAttack → target swapped by updateHittable: " +
                    "${KillAuraDebug.describeEntity(initialTarget)} → ${KillAuraDebug.describeEntity(currentTarget)}"
            )
        }

        if (KillAuraTargetTracker.hittable && currentTarget.hurtTime > KillAura.hurtTime) {
            KillAuraDebug.gate(
                "runAttack → hittable=true but target hurtTime=${currentTarget.hurtTime} > hurtTime=${KillAura.hurtTime}, waiting"
            )
            return
        }

        // Check if enemy is not hittable
        if (!KillAuraTargetTracker.hittable && KillAura.options.rotationsActive) {
            KillAuraDebug.hit(
                "runAttack → target NOT hittable, entering fail-swing path " +
                    "(swing=${KillAura.swing} failSwing=${KillAura.failSwing})"
            )
            if (KillAura.swing && KillAura.failSwing) {
                val rotation = currentRotation ?: player.rotation

                // Can humans keep click consistency when performing massive
                // rotation changes? (10-30 rotation difference / large mouse
                // movements.) If not, hold the click.
                val rotDiff = rotationDifference(rotation)
                if (rotDiff > KillAura.maxRotationDifferenceToSwing) {
                    val shouldIgnore = KillAura.swingWhenTicksLate.isActive() &&
                        ticksSinceClick() >= KillAura.ticksLateToSwing

                    if (!shouldIgnore) {
                        KillAuraDebug.miss(
                            "runAttack → rotationDifference=$rotDiff > " +
                                "maxRotationDifferenceToSwing=${KillAura.maxRotationDifferenceToSwing} " +
                                "and not swingWhenTicksLate, holding click"
                        )
                        return
                    } else {
                        KillAuraDebug.miss(
                            "runAttack → rotationDifference=$rotDiff > " +
                                "maxRotationDifferenceToSwing but ticksSinceClick=${ticksSinceClick()} " +
                                "≥ ticksLateToSwing=${KillAura.ticksLateToSwing}, ignoring gate"
                        )
                    }
                }

                runWithModifiedRaycastResult(
                    rotation,
                    KillAura.range.toDouble(),
                    KillAura.throughWallsRange.toDouble()
                ) { mop ->
                    KillAuraDebug.miss(
                        "runAttack → runWithModifiedRaycastResult: " +
                            "typeOfHit=${mop.typeOfHit} entityHit=${KillAuraDebug.describeEntity(mop.entityHit)} " +
                            "hitVec=${KillAuraDebug.describeVec(mop.hitVec)}"
                    )

                    if (KillAura.swingOnlyInAir && !mop.typeOfHit.isMiss) {
                        KillAuraDebug.miss("runAttack → swingOnlyInAir and not a miss, skipping swing")
                        return@runWithModifiedRaycastResult
                    }

                    // Miss cool-down: when you click and miss you get a 10
                    // tick cool-down. If you click and release immediately
                    // the cool-down drops to 0. Most humans release 1-2 ticks
                    // after clicking, leaving them with an average of 10 CPS.
                    if (KillAura.respectMissCooldown && ticksSinceClick() <= 1 && mop.typeOfHit.isMiss) {
                        KillAuraDebug.miss(
                            "runAttack → respectMissCooldown and miss within 1 tick of last click " +
                                "(ticksSinceClick=${ticksSinceClick()}), suppressing swing"
                        )
                        return@runWithModifiedRaycastResult
                    }

                    val shouldEnterBlockBreakProgress =
                        !shouldDelayClick(mop.typeOfHit) ||
                            lastAttackTickData?.first?.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK

                    if (shouldEnterBlockBreakProgress) {
                        if (manipulateInventory && isFirstClick) {
                            KillAuraDebug.evt("runAttack → shouldEnterBlockBreakProgress + isFirstClick, serverOpenInventory=false")
                            serverOpenInventory = false
                        }
                    }

                    val prevCooldown = mc.leftClickCounter

                    // Is any GUI coming from our client?
                    val isAnyClientGuiActive = mc.currentScreen?.javaClass?.`package`?.name?.contains(
                        FDPClient.CLIENT_NAME, ignoreCase = true
                    ) == true

                    if (isAnyClientGuiActive) {
                        KillAuraDebug.evt("runAttack → client GUI active, setting mc.leftClickCounter=0 (was=$prevCooldown)")
                        mc.leftClickCounter = 0
                    }

                    if (!shouldDelayClick(mop.typeOfHit)) {
                        val previousAttackTickData = lastAttackTickData
                        lastAttackTickData = mop to runTimeTicks

                        if (mop.typeOfHit.isEntity) {
                            val entity = mop.entityHit

                            if (entity is EntityLivingBase && isSelected(entity, true)) {
                                KillAuraDebug.atk(
                                    "runAttack → fail-swing path hit entity ${KillAuraDebug.describeEntity(entity)} → attackEntity"
                                )
                                attackEntity(entity, isLastClick)
                            } else {
                                KillAuraDebug.miss(
                                    "runAttack → fail-swing path hit non-target entity " +
                                        "${KillAuraDebug.describeEntity(entity)} (isSelected=${isSelected(entity, true)}), reverting"
                                )
                                lastAttackTickData = previousAttackTickData
                            }
                        } else {
                            // Imitate game click
                            KillAuraDebug.miss(
                                "runAttack → fail-swing path typeOfHit=${mop.typeOfHit}, imitating mc.clickMouse()"
                            )
                            mc.clickMouse()

                            if (KillAura.renderBoxOnSwingFail) {
                                synchronized(swingFails) {
                                    val centerDistance = (currentTarget.hitBox.center - player.eyes).lengthVector()
                                    val spot = player.eyes + getVectorForRotation(rotation) * centerDistance

                                    swingFails += SwingFailData(spot, System.currentTimeMillis())
                                    KillAuraDebug.miss(
                                        "runAttack → fail-swing box recorded at ${KillAuraDebug.describeVec(spot)} " +
                                            "(target=${KillAuraDebug.describeEntity(currentTarget)})"
                                    )
                                }
                            }
                        }
                    } else {
                        KillAuraDebug.miss(
                            "runAttack → shouldDelayClick(${mop.typeOfHit}) is true, skipping this click"
                        )
                    }

                    if (shouldEnterBlockBreakProgress && isLastClick) {
                        KillAuraDebug.evt(
                            "runAttack → shouldEnterBlockBreakProgress + isLastClick: " +
                                "mc.sendClickBlockToController(true) + nextTick(false) + clicks=0"
                        )
                        // Updates the block breaking progress, sending an
                        // animation packet. Setting this function's
                        // parameter to `false` would still obey vanilla
                        // clicking logic, but only if you were releasing
                        // the click button immediately after pressing.
                        mc.sendClickBlockToController(true)
                        KillAura.nextTick {
                            mc.sendClickBlockToController(false)
                            KillAuraClicker.clicks = 0
                            if (manipulateInventory) serverOpenInventory = true
                        }
                    }

                    if (isAnyClientGuiActive) {
                        KillAuraDebug.evt("runAttack → restoring mc.leftClickCounter=$prevCooldown")
                        mc.leftClickCounter = prevCooldown
                    }
                }
            }

            return
        }

        // Close inventory when open
        if (manipulateInventory && isFirstClick) {
            KillAuraDebug.evt("runAttack → manipulateInventory + isFirstClick, serverOpenInventory=false")
            serverOpenInventory = false
        }

        KillAuraAutoBlock.blockStopInDead = false

        if (!multi) {
            KillAuraDebug.atk(
                "runAttack → single-target path: " +
                    "hittable=${KillAuraTargetTracker.hittable} target=${KillAuraDebug.describeEntity(currentTarget)}"
            )
            attackEntity(currentTarget, isLastClick)
        } else {
            KillAuraDebug.atk(
                "runAttack → multi-target path: scanning loadedEntityList for enemies in range"
            )
            var targets = 0
            for (entity in world.loadedEntityList) {
                val distance = player.getDistanceToEntityBox(entity)
                if (entity is EntityLivingBase && KillAuraTargetTracker.isEnemy(entity) &&
                    distance <= KillAuraRange.getRange(entity)
                ) {
                    KillAuraDebug.atk(
                        "runAttack   multi → attacking ${KillAuraDebug.describeEntity(entity)} " +
                            "(dist=$distance reach=${KillAuraRange.getRange(entity)})"
                    )
                    attackEntity(entity, isLastClick)
                    targets += 1
                    if (KillAura.limitedMultiTargets != 0 && KillAura.limitedMultiTargets <= targets) {
                        KillAuraDebug.atk("runAttack   multi → limitedMultiTargets=${KillAura.limitedMultiTargets} reached, breaking")
                        break
                    }
                }
            }
        }

        if (!isLastClick) return

        val switchMode = KillAura.targetMode == "Switch"

        if (!switchMode || switchTimer.hasTimePassed(KillAura.switchDelay)) {
            KillAuraDebug.track(
                "runAttack → pushing prevTargetEntities += #${currentTarget.entityId} " +
                    "(switchMode=$switchMode switchDelay=${KillAura.switchDelay} " +
                    "timerPassed=${switchTimer.hasTimePassed(KillAura.switchDelay)})"
            )
            KillAuraTargetTracker.prevTargetEntities += currentTarget.entityId
            if (switchMode) {
                switchTimer.reset()
            }
        } else {
            KillAuraDebug.track(
                "runAttack → switchDelay not elapsed, not adding #${currentTarget.entityId} to prevTargetEntities"
            )
        }

        if (manipulateInventory) {
            KillAuraDebug.evt("runAttack → restoring serverOpenInventory=true")
            serverOpenInventory = true
        }
    }

    /**
     * Deliver the attack packet. Wraps `attackEntityWithModifiedSprint`
     * so the client can keep-sprint, and re-arms auto-block.
     */
    private fun attackEntity(entity: EntityLivingBase, isLastClick: Boolean) {
        val thePlayer = mc.thePlayer ?: return

        if (KillAuraRequirements.shouldPrioritize()) {
            KillAuraDebug.gate("attackEntity → shouldPrioritize, aborting attack on ${KillAuraDebug.describeEntity(entity)}")
            return
        }

        if (thePlayer.isBlocking &&
            (KillAura.autoBlock == "Off" && KillAuraAutoBlock.blockStatus ||
                KillAura.autoBlock == "Packet" && KillAura.releaseAutoBlock)
        ) {
            KillAuraDebug.blk(
                "attackEntity → thePlayer.isBlocking=true, stopping block to attack " +
                    "(autoBlock=${KillAura.autoBlock} releaseAutoBlock=${KillAura.releaseAutoBlock})"
            )
            KillAuraAutoBlock.stopBlocking()
            if (!KillAura.ignoreTickRule || KillAura.autoBlock == "Off") {
                return
            }
        }

        if (shouldDelayClick(MovingObjectPosition.MovingObjectType.ENTITY)) {
            KillAuraDebug.gate("attackEntity → shouldDelayClick(ENTITY)=true, skipping")
            return
        }

        // Snapshot the target's health *before* the attack so we can show
        // the actual damage the server applied (best-effort — Minecraft
        // resolves the damage on the same tick but the field is updated
        // synchronously from the client perspective).
        val healthBefore = entity.health
        val maxHealth = entity.maxHealth
        val distance = thePlayer.getDistanceToEntityBox(entity)
        val reach = KillAuraRange.getRange(entity)
        val targetSnapshot = "${entity.javaClass.simpleName}#${entity.entityId}"
        val playerSnapshot = "${thePlayer.javaClass.simpleName}#${thePlayer.entityId} @ ${KillAuraDebug.describeVec(thePlayer.getPositionEyes(1f))}"

        KillAuraDebug.atk(
            "attackEntity → SENDING C02PacketUseEntity(ATTACK) to $targetSnapshot " +
                "(distance=$distance reach=$reach, " +
                "h=$healthBefore/$maxHealth, hurtTime=${entity.hurtTime}, hurtResist=${entity.hurtResistantTime}) " +
                "from $playerSnapshot " +
                "keepSprint=${KillAura.keepSprint} blinkAutoBlock=${KillAura.blinkAutoBlock} " +
                "blinked=${KillAuraAutoBlock.blinked}"
        )

        if (!KillAura.blinkAutoBlock || !KillAuraAutoBlock.blinked) {
            val keepSprintModuleActive = FDPClient.moduleManager["KeepSprint"]?.handleEvents() == true
            val affectSprint = false.takeIf { keepSprintModuleActive || KillAura.keepSprint }

            thePlayer.attackEntityWithModifiedSprint(entity, affectSprint) {
                if (KillAura.swing) thePlayer.swingItem()
            }

            val healthAfter = entity.health
            val delta = healthBefore - healthAfter
            KillAuraDebug.atk(
                "attackEntity → AFTER attack $targetSnapshot: " +
                    "h=$healthBefore→$healthAfter (Δ=$delta) " +
                    "swing=${KillAura.swing} hurtTime=${entity.hurtTime} " +
                    "hurtResist=${entity.hurtResistantTime} alive=${entity.isEntityAlive && entity.health > 0}"
            )
        } else {
            KillAuraDebug.atk(
                "attackEntity → blinkAutoBlock=ON and blinked=true, " +
                    "ATTACK SUPPRESSED (queued for blink release) for $targetSnapshot"
            )
        }

        // Start blocking after attack
        if (KillAura.autoBlock != "Off" &&
            (thePlayer.isBlocking || KillAuraAutoBlock.canBlock) &&
            (!KillAura.blinkAutoBlock && isLastClick ||
                KillAura.blinkAutoBlock && (!KillAuraAutoBlock.blinked || !KillAuraAutoBlock.blinking))
        ) {
            KillAuraDebug.blk(
                "attackEntity → re-engaging block after attack: " +
                    "canBlock=${KillAuraAutoBlock.canBlock} isLastClick=$isLastClick " +
                    "blinkAutoBlock=${KillAura.blinkAutoBlock} blinked=${KillAuraAutoBlock.blinked} " +
                    "blinking=${KillAuraAutoBlock.blinking}"
            )
            KillAuraAutoBlock.startBlocking(entity, KillAura.interactAutoBlock, KillAura.autoBlock == "Fake")
        }

        resetLastAttackedTicks()
    }

    /** Reset transient state — used by `KillAura.onToggle(false)`. */
    fun reset() {
        KillAuraDebug.evt(
            "KillAuraClicker.reset(): clicks=$clicks attackDelay=$attackDelay " +
                "lastAttackTickData=$lastAttackTickData swingFails=${swingFails.size}"
        )
        attackTimer.reset()
        clicks = 0
        attackDelay = 0
        lastAttackTickData = null
        synchronized(swingFails) { swingFails.clear() }
    }

    /**
     * Render the little marker boxes for failed-swing events. Mirrors the
     * original `handleFailedSwings` method.
     */
    fun handleFailedSwings() {
        if (!KillAura.renderBoxOnSwingFail) return

        val box = AxisAlignedBB(0.0, 0.0, 0.0, 0.05, 0.05, 0.05)

        synchronized(swingFails) {
            val fadeSeconds = KillAura.renderBoxFadeSeconds * 1000L
            val colorSettings = KillAura.renderBoxColor
            val renderManager = mc.renderManager

            val before = swingFails.size
            swingFails.removeAll {
                val timestamp = (System.currentTimeMillis() - it.startTime).toFloat()
                val normalized = (timestamp / fadeSeconds.toFloat()).coerceIn(0f, 1f)
                val transparency = (0f..255f).lerpWith(1f - normalized)

                val offsetBox = box.offset(it.vec3 - renderManager.renderPos)

                RenderUtils.drawAxisAlignedBB(offsetBox, colorSettings.color(a = transparency.roundToInt()))

                timestamp > fadeSeconds
            }
            val after = swingFails.size
            if (KillAuraDebug.enabled && (before != after || before > 0)) {
                KillAuraDebug.miss(
                    "handleFailedSwings: drew ${before - after} expired boxes, " +
                        "remaining=$after (fadeSeconds=${KillAura.renderBoxFadeSeconds})"
                )
            }
        }
    }

    /** Used by the [switchTimer] in [runAttack]. */
    private val switchTimer = MSTimer()
}

/**
 * Persistent record of one failed swing, used to render the little box on
 * screen for `renderBoxFadeSeconds`.
 */
data class SwingFailData(val vec3: Vec3, val startTime: Long)
