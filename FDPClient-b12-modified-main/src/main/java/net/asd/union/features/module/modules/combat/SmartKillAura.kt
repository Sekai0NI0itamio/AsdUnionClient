/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 *
 * SmartKillAura keeps the regular KillAura untouched and focuses on landing hits
 * closer to the real 1.8.9 damage window instead of dumping packets into hurt
 * resistance.
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.FDPClient
import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.config.int
import net.asd.union.event.Render2DEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.WorldEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.attack.EntityUtils.isSelected
import net.asd.union.utils.client.MinecraftInstance.Companion.mc
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.attackEntityWithModifiedSprint
import net.asd.union.utils.extensions.eyes
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.asd.union.utils.timing.MSTimer
import net.asd.union.utils.timing.TimeUtils.randomClickDelay
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.START_SPRINTING
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.STOP_SPRINTING
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

object SmartKillAura : Module("SmartKillAura", Category.COMBAT, Keyboard.KEY_H, hideModule = false) {

    private const val MAX_COMBINED_ATTACKS = 8
    private const val MAX_BUFFER_FILL_ITERATIONS = 64

    private val attackMode by choices("AttackMode", arrayOf("Optimal", "Criticals", "WTap", "Hybrid"), "Optimal")

    private val range by float("Range", 3.0f, 1f..8f)
    private val fov by float("FOV", 180f, 0f..180f)
    private val priority by choices("Priority", arrayOf("Distance", "Health", "Angle", "HurtResistant"), "Distance")
    private val respectTargeter by boolean("RespectTargeter", true)

    private val smartTiming by boolean("SmartTiming", true)
    private val preAttackTicks by int("PreAttackTicks", 1, 0..3) { smartTiming }
    private val predictedWindowCompensationMs by int("PredictedWindowCompensationMs", 40, 0..150) { smartTiming }

    private val minCPS by int("MinCPS", 20, 0..100)
    private val maxCPS by int("MaxCPS", 20, 0..100)
    private val keepSprint by boolean("KeepSprint", false)
    private val swing by boolean("Swing", true)

    private val autoCrits by boolean("AutoCrits", false) { attackMode == "Criticals" || attackMode == "Hybrid" }
    private val critChance by int("CritChance", 50, 0..100) { attackMode == "Hybrid" && autoCrits }
    private val autoJump by boolean("AutoJump", true) { attackMode == "Criticals" || attackMode == "Hybrid" }

    private val wTapMode by choices("WTapMode", arrayOf("Send", "Release", "Both"), "Send") {
        attackMode == "WTap" || attackMode == "Hybrid"
    }
    private val wTapDelay by int("WTapDelay", 50, 0..200) { attackMode == "WTap" || attackMode == "Hybrid" }

    private val lowHealthBoost by boolean("LowHealthBoost", true)
    private val healthThreshold by float("HealthThreshold", 6f, 1f..20f) { lowHealthBoost }
    private val lowHealthCPS by int("LowHealthCPS", 20, 0..100) { lowHealthBoost }

    private val debugHud by boolean("DebugHUD", true)

    private data class HurtInfo(
        var lastObservedHurtTime: Int = 0,
        var lastObservedHurtResistantTime: Int = 0,
        var lastObservationAt: Long = 0L,
        var lastAttackAt: Long = 0L,
        var predictedUnlockAt: Long = 0L
    )

    private enum class HurtState {
        NONE,
        INVULNERABLE,
        COOLDOWN,
        READY,
        WAITING_CRIT
    }

    private val targetHurtInfo = mutableMapOf<Int, HurtInfo>()
    private val wTapTimer = MSTimer()

    private var currentTarget: EntityLivingBase? = null
    private var currentDelay = 0
    private var bufferedClicks = 0
    private var lastBurstClicks = 0
    private var queuedTargetId = -1
    private var nextBufferedClickAt = 0L

    private var pendingCritTargetId = -1
    private var critJumpTick = -1

    override fun onToggle(state: Boolean) {
        if (!state) {
            resetCombatState()
            return
        }

        currentDelay = nextAttackDelay(null)
        nextBufferedClickAt = 0L
    }

    private fun resetCombatState() {
        targetHurtInfo.clear()
        currentTarget = null
        currentDelay = 0
        bufferedClicks = 0
        lastBurstClicks = 0
        queuedTargetId = -1
        nextBufferedClickAt = 0L
        pendingCritTargetId = -1
        critJumpTick = -1
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (player.isDead || player.health <= 0f || player.isSpectator) {
            resetCombatState()
            return@handler
        }

        updateTargetHurtStates()
        selectTarget(player)

        val target = currentTarget ?: run {
            clearPendingCrit()
            return@handler
        }

        if (!isValidTarget(player, target, ignoreFov = respectTargeter && KillAuraTargeter.state)) {
            currentTarget = null
            clearPendingCrit()
            resetBufferedClicks()
            return@handler
        }

        updateBufferedClicks(target)

        if (bufferedClicks <= 0) {
            return@handler
        }

        if (pendingCritTargetId == target.entityId) {
            if (!handlePendingCritical(player, target)) {
                clearPendingCrit()
            }
            return@handler
        }

        if (!canAttackNow(target)) {
            return@handler
        }

        if (shouldQueueCritical(player)) {
            queueCritical(player, target)
            return@handler
        }

        performBufferedAttacks(target)
    }

    private fun selectTarget(player: EntityPlayerSP) {
        resolveTargeterTarget(player)?.let {
            currentTarget = it
            return
        }

        val world = mc.theWorld ?: run {
            currentTarget = null
            return
        }

        val lookVec = normalizedLookVector(player.rotationYaw, player.rotationPitch)

        var bestTarget: EntityLivingBase? = null
        var bestValue = Double.MAX_VALUE

        for (rawEntity in world.loadedEntityList) {
            val entity = rawEntity as? EntityLivingBase ?: continue
            if (!isValidTarget(player, entity)) continue

            val score = when (priority.lowercase()) {
                "health" -> entity.health.toDouble()
                "angle" -> angleToEntity(player, entity, lookVec)
                "hurtresistant" -> entity.hurtResistantTime.toDouble()
                else -> player.getDistanceToEntityBox(entity)
            }

            if (score < bestValue) {
                bestValue = score
                bestTarget = entity
            }
        }

        currentTarget = bestTarget
    }

    private fun resolveTargeterTarget(player: EntityPlayerSP): EntityLivingBase? {
        if (!respectTargeter || !KillAuraTargeter.state) {
            return null
        }

        val target = KillAuraTargeter.getTargetEntity() ?: return null
        return target.takeIf { isValidTarget(player, it, ignoreFov = true) }
    }

    private fun isValidTarget(player: EntityPlayerSP, entity: EntityLivingBase, ignoreFov: Boolean = false): Boolean {
        if (entity === player || entity is EntityArmorStand || !entity.isEntityAlive) {
            return false
        }

        if (!isSelected(entity, true)) {
            return false
        }

        if (player.getDistanceToEntityBox(entity) > range) {
            return false
        }

        if (ignoreFov || fov >= 180f) {
            return true
        }

        val lookVec = normalizedLookVector(player.rotationYaw, player.rotationPitch)
        return angleToEntity(player, entity, lookVec) <= fov / 2.0
    }

    private fun angleToEntity(player: EntityPlayerSP, entity: EntityLivingBase, lookVec: Vec3?): Double {
        val effectiveLook = lookVec ?: return 0.0
        val eyes = player.eyes
        val center = Vec3(
            entity.posX,
            entity.entityBoundingBox.minY + entity.eyeHeight.toDouble() * 0.9,
            entity.posZ
        )

        val dirX = center.xCoord - eyes.xCoord
        val dirY = center.yCoord - eyes.yCoord
        val dirZ = center.zCoord - eyes.zCoord
        val distance = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)

        if (distance < 1.0E-6) {
            return 0.0
        }

        val dot = (effectiveLook.xCoord * dirX + effectiveLook.yCoord * dirY + effectiveLook.zCoord * dirZ) / distance
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
    }

    private fun normalizedLookVector(yaw: Float, pitch: Float): Vec3? {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val raw = Vec3(
            -kotlin.math.sin(yawRad) * cos(pitchRad),
            -kotlin.math.sin(pitchRad),
            kotlin.math.cos(yawRad) * cos(pitchRad)
        )

        val length = raw.lengthVector()
        return if (length < 1.0E-6) null else Vec3(raw.xCoord / length, raw.yCoord / length, raw.zCoord / length)
    }

    private fun updateTargetHurtStates() {
        val now = System.currentTimeMillis()
        val world = mc.theWorld ?: return

        targetHurtInfo.entries.removeIf { (entityId, _) ->
            world.loadedEntityList.none { it.entityId == entityId }
        }

        for (rawEntity in world.loadedEntityList) {
            val entity = rawEntity as? EntityLivingBase ?: continue
            val info = targetHurtInfo.getOrPut(entity.entityId) { HurtInfo() }

            if (entity.hurtTime <= 0 && entity.hurtResistantTime <= 0 && now > info.predictedUnlockAt) {
                continue
            }

            info.lastObservedHurtTime = entity.hurtTime
            info.lastObservedHurtResistantTime = entity.hurtResistantTime
            info.lastObservationAt = now
        }
    }

    private fun canAttackNow(target: EntityLivingBase): Boolean {
        if (!smartTiming) {
            return true
        }

        val info = targetHurtInfo.getOrPut(target.entityId) { HurtInfo() }
        val now = System.currentTimeMillis()
        val damageWindowTick = resolveDamageWindowTick(target)
        val earlyAttackTick = (damageWindowTick + preAttackTicks).coerceAtMost(resolveMaxHurtResistantTime(target))
        val observedReady = info.lastObservationAt > info.lastAttackAt && target.hurtResistantTime <= earlyAttackTick
        val predictedReady = now + predictedWindowCompensationMs >= info.predictedUnlockAt

        if (info.lastAttackAt == 0L) {
            return target.hurtResistantTime <= earlyAttackTick
        }

        return observedReady || predictedReady
    }

    private fun resolveMaxHurtResistantTime(target: EntityLivingBase): Int {
        return target.maxHurtResistantTime.takeIf { it > 0 } ?: 20
    }

    private fun resolveDamageWindowTick(target: EntityLivingBase): Int {
        return (resolveMaxHurtResistantTime(target) / 2).coerceAtLeast(1)
    }

    private fun shouldQueueCritical(player: EntityPlayerSP): Boolean {
        if (!shouldUseCritAttempt() || !autoJump || !player.onGround) {
            return false
        }

        if (player.isOnLadder || player.isInWater || player.isInLava || player.ridingEntity != null) {
            return false
        }

        return !player.capabilities.isFlying
    }

    private fun shouldUseCritAttempt(): Boolean {
        if (!autoCrits) {
            return false
        }

        return when (attackMode.lowercase()) {
            "criticals" -> true
            "hybrid" -> Math.random() * 100.0 < critChance
            else -> false
        }
    }

    private fun queueCritical(player: EntityPlayerSP, target: EntityLivingBase) {
        pendingCritTargetId = target.entityId
        critJumpTick = player.ticksExisted
        player.jump()
    }

    private fun handlePendingCritical(player: EntityPlayerSP, target: EntityLivingBase): Boolean {
        if (target.entityId != pendingCritTargetId) {
            return false
        }

        if (player.ticksExisted - critJumpTick > 10) {
            return false
        }

        if (canPerformCriticalHit(player) && canAttackNow(target)) {
            performBufferedAttacks(target)
            clearPendingCrit()
            return true
        }

        return true
    }

    private fun canPerformCriticalHit(player: EntityPlayerSP): Boolean {
        return !player.onGround &&
            player.fallDistance > 0f &&
            !player.isOnLadder &&
            !player.isInWater &&
            !player.isInLava &&
            player.ridingEntity == null &&
            !player.capabilities.isFlying
    }

    private fun performBufferedAttacks(target: EntityLivingBase) {
        val player = mc.thePlayer ?: return

        if ((attackMode == "WTap" || attackMode == "Hybrid") && wTapTimer.hasTimePassed(wTapDelay.toLong())) {
            performWTap(player)
            wTapTimer.reset()
        }

        val keepSprintModuleActive = FDPClient.moduleManager["KeepSprint"]?.handleEvents() == true
        val affectSprint = false.takeIf { keepSprint || keepSprintModuleActive }
        val combinedAttacks = bufferedClicks.coerceIn(1, MAX_COMBINED_ATTACKS)

        repeat(combinedAttacks) { index ->
            player.attackEntityWithModifiedSprint(target, affectSprint) {
                if (swing && index == 0) {
                    player.swingItem()
                }
            }
        }

        bufferedClicks = 0
        lastBurstClicks = combinedAttacks
        recordAttack(target)
        currentDelay = nextAttackDelay(target)
        nextBufferedClickAt = System.currentTimeMillis() + currentDelay
    }

    private fun recordAttack(target: EntityLivingBase) {
        val now = System.currentTimeMillis()
        val info = targetHurtInfo.getOrPut(target.entityId) { HurtInfo() }
        val windowTicks = (resolveDamageWindowTick(target) - preAttackTicks).coerceAtLeast(1)

        info.lastAttackAt = now
        info.predictedUnlockAt = now + windowTicks * 50L
        info.lastObservedHurtTime = target.hurtTime
        info.lastObservedHurtResistantTime = target.hurtResistantTime
        info.lastObservationAt = now
    }

    private fun nextAttackDelay(target: EntityLivingBase?): Int {
        val boostedCps = if (lowHealthBoost && target != null && target.health <= healthThreshold) {
            lowHealthCPS
        } else {
            maxCPS
        }

        val maxValue = maxOf(minCPS, boostedCps)
        val minValue = minOf(minCPS, boostedCps)
        return randomClickDelaySafe(minValue, maxValue)
    }

    private fun updateBufferedClicks(target: EntityLivingBase) {
        val now = System.currentTimeMillis()

        if (queuedTargetId != target.entityId) {
            queuedTargetId = target.entityId
            bufferedClicks = 0
            lastBurstClicks = 0
            nextBufferedClickAt = now
            currentDelay = nextAttackDelay(target)
        }

        if (currentDelay == Int.MAX_VALUE) {
            bufferedClicks = 0
            nextBufferedClickAt = Long.MAX_VALUE
            return
        }

        if (nextBufferedClickAt == 0L) {
            nextBufferedClickAt = now
        }

        var fillIterations = 0

        while (now >= nextBufferedClickAt && fillIterations++ < MAX_BUFFER_FILL_ITERATIONS) {
            bufferedClicks = (bufferedClicks + 1).coerceAtMost(MAX_COMBINED_ATTACKS)
            currentDelay = nextAttackDelay(target)

            if (currentDelay == Int.MAX_VALUE) {
                nextBufferedClickAt = Long.MAX_VALUE
                break
            }

            nextBufferedClickAt += currentDelay.toLong().coerceAtLeast(1L)
        }

        if (fillIterations >= MAX_BUFFER_FILL_ITERATIONS) {
            nextBufferedClickAt = now + currentDelay.coerceAtLeast(1).toLong()
        }
    }

    private fun resetBufferedClicks() {
        bufferedClicks = 0
        lastBurstClicks = 0
        queuedTargetId = -1
        nextBufferedClickAt = 0L
    }

    private fun randomClickDelaySafe(minValue: Int, maxValue: Int): Int {
        val normalizedMin = minValue.coerceAtLeast(0)
        val normalizedMax = maxValue.coerceAtLeast(0)

        if (normalizedMin == 0 && normalizedMax == 0) {
            return Int.MAX_VALUE
        }

        val safeMin = normalizedMin.coerceAtLeast(1)
        val safeMax = normalizedMax.coerceAtLeast(1)

        return randomClickDelay(minOf(safeMin, safeMax), maxOf(safeMin, safeMax)).coerceAtLeast(1)
    }

    private fun performWTap(player: EntityPlayerSP) {
        when (wTapMode.lowercase()) {
            "release" -> {
                sendPacket(C0BPacketEntityAction(player, STOP_SPRINTING))
                player.isSprinting = false
                player.serverSprintState = false
            }

            "both" -> {
                sendPackets(
                    C0BPacketEntityAction(player, STOP_SPRINTING),
                    C0BPacketEntityAction(player, START_SPRINTING),
                    C0BPacketEntityAction(player, STOP_SPRINTING),
                    C0BPacketEntityAction(player, START_SPRINTING)
                )
                player.isSprinting = true
                player.serverSprintState = true
            }

            else -> {
                sendPackets(
                    C0BPacketEntityAction(player, STOP_SPRINTING),
                    C0BPacketEntityAction(player, START_SPRINTING)
                )
                player.isSprinting = true
                player.serverSprintState = true
            }
        }
    }

    private fun clearPendingCrit() {
        pendingCritTargetId = -1
        critJumpTick = -1
    }

    private fun getTargetHurtState(target: EntityLivingBase?): HurtState {
        val resolvedTarget = target ?: return HurtState.NONE

        if (resolvedTarget.entityId == pendingCritTargetId) {
            return HurtState.WAITING_CRIT
        }

        val damageWindowTick = resolveDamageWindowTick(resolvedTarget)
        if (resolvedTarget.hurtResistantTime > damageWindowTick + preAttackTicks) {
            return HurtState.INVULNERABLE
        }

        val info = targetHurtInfo[resolvedTarget.entityId] ?: return HurtState.READY
        return if (System.currentTimeMillis() + predictedWindowCompensationMs < info.predictedUnlockAt) {
            HurtState.COOLDOWN
        } else {
            HurtState.READY
        }
    }

    val onRender2D = handler<Render2DEvent> {
        if (!debugHud) {
            return@handler
        }

        val target = currentTarget ?: return@handler
        val player = mc.thePlayer ?: return@handler
        val state = getTargetHurtState(target)
        val info = targetHurtInfo[target.entityId]
        val predictedReadyMs = info?.let { (it.predictedUnlockAt - System.currentTimeMillis()).coerceAtLeast(0L) } ?: 0L

        val stateText = when (state) {
            HurtState.READY -> "READY"
            HurtState.COOLDOWN -> "COOLDOWN"
            HurtState.INVULNERABLE -> "INVULNERABLE"
            HurtState.WAITING_CRIT -> "WAITING_CRIT"
            HurtState.NONE -> "NO_DATA"
        }

        val stateColor = when (state) {
            HurtState.READY -> Color.GREEN.rgb
            HurtState.COOLDOWN -> Color.YELLOW.rgb
            HurtState.INVULNERABLE -> Color.RED.rgb
            HurtState.WAITING_CRIT -> Color.CYAN.rgb
            HurtState.NONE -> Color.LIGHT_GRAY.rgb
        }

        val distance = String.format("%.2f", player.getDistanceToEntityBox(target))

        Fonts.font40.drawStringWithShadow("§6[SmartKillAura]", 5f, 5f, Color.ORANGE.rgb)
        Fonts.font40.drawStringWithShadow("§eMode: §f$attackMode", 5f, 20f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eTarget: §f${target.name}", 5f, 35f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eHealth: §f${target.health.roundToInt()} HP", 5f, 50f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eDistance: §f$distance", 5f, 65f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eState: ", 5f, 80f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow(stateText, 65f, 80f, stateColor)
        Fonts.font40.drawStringWithShadow(
            "§eHurtResistant: §f${target.hurtResistantTime}  §eHurtTime: §f${target.hurtTime}",
            5f,
            95f,
            Color.WHITE.rgb
        )
        Fonts.font40.drawStringWithShadow("§ePredicted Ready: §f${predictedReadyMs}ms", 5f, 110f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eQueued Clicks: §f${bufferedClicks}  §eLast Burst: §f${lastBurstClicks}", 5f, 125f, Color.WHITE.rgb)
    }

    val onWorld = handler<WorldEvent> {
        resetCombatState()
    }

    override val tag
        get() = attackMode
}
