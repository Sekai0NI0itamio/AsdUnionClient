/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 * 
 * SmartKillAura - Experimental standalone combat module
 * Addresses Minecraft 1.8.9 damage mechanics issues:
 * - Damage immunity during hurtTime window (10 ticks)
 * - Sprint attack knockback reduction
 * - Critical hit requirements
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.config.int
import net.asd.union.event.*
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.MinecraftInstance.Companion.mc
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.*
import net.asd.union.utils.rotation.RaycastUtils.raycastEntity
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.isRotationFaced
import net.asd.union.utils.timing.MSTimer
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.*
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.sqrt

object SmartKillAura : Module("SmartKillAura", Category.COMBAT, Keyboard.KEY_H, hideModule = false) {

    // Attack Mode Selection
    private val attackMode by choices("AttackMode", arrayOf("Optimal", "Criticals", "WTap", "Hybrid"), "Optimal")
    
    // Target Selection
    private val range by float("Range", 3.7f, 1f..8f)
    private val fov by float("FOV", 180f, 0f..180f)
    private val priority by choices("Priority", arrayOf("Distance", "Health", "Angle"), "Distance")
    
    // Invulnerability Timing
    private val waitForInvulnerability by boolean("WaitForInvulnerability", true)
    private val invulnerabilityWaitTime by int("InvulnerabilityWaitTime", 250, 100..500) { waitForInvulnerability }
    
    // CPS Control
    private val controlCPS by boolean("ControlCPS", true)
    private val maxCPS by int("MaxCPS", 2, 1..20) { controlCPS }
    private val minCPS by int("MinCPS", 2, 1..20) { controlCPS }
    
    // Critical Hits
    private val autoCrits by boolean("AutoCrits", false) { attackMode == "Criticals" || attackMode == "Hybrid" }
    private val critChance by int("CritChance", 50, 0..100) { attackMode == "Hybrid" && autoCrits }
    private val autoJump by boolean("AutoJump", true) { attackMode == "Criticals" || (attackMode == "Hybrid" && autoCrits) }
    
    // W-Tap (Sprint Reset)
    private val wTapMode by choices("WTapMode", arrayOf("Send", "Release", "Both"), "Send") { attackMode == "WTap" }
    private val wTapDelay by int("WTapDelay", 50, 0..200) { attackMode == "WTap" }
    
    // Low Health Aggression
    private val lowHealthBoost by boolean("LowHealthBoost", true)
    private val healthThreshold by float("HealthThreshold", 6f, 1f..20f) { lowHealthBoost }
    private val lowHealthCPS by int("LowHealthCPS", 6, 1..20) { lowHealthBoost }
    
    // Target Tracking
    private val targetHurtInfo = mutableMapOf<Int, HurtInfo>()
    private val attackTimer = MSTimer()
    private val wTapTimer = MSTimer()
    private var currentDelay = 0
    
    private var currentTarget: EntityLivingBase? = null
    private var wasInAir = false
    private var lastCritJump = 0
    
    private data class HurtInfo(
        val hitTime: Long,
        val hurtTimeRemaining: Int
    )

    private enum class HurtState {
        NONE,
        INVULNERABLE,
        COOLDOWN,
        READY
    }

    override fun onToggle(state: Boolean) {
        if (!state) {
            targetHurtInfo.clear()
            currentTarget = null
            attackTimer.reset()
        }
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler
        
        if (player.isSpectator || player.isDead || player.health <= 0) {
            currentTarget = null
            return@handler
        }

        updateTargetHurtStates()
        selectTarget()

        val target = currentTarget ?: return@handler

        // Check if target is still valid
        if (!target.isEntityAlive || player.getDistanceToEntityBox(target) > range) {
            currentTarget = null
            return@handler
        }

        // Check invulnerability
        if (waitForInvulnerability && !canAttack(target)) {
            return@handler
        }

        // Handle attack timing
        val targetHealth = target.health
        val effectiveCPS = if (lowHealthBoost && targetHealth <= healthThreshold) lowHealthCPS else maxCPS
        val effectiveMinCPS = if (lowHealthBoost && targetHealth <= healthThreshold) minOf(lowHealthCPS, minCPS) else minCPS
        
        if (controlCPS && attackTimer.hasTimePassed(currentDelay)) {
            if (canAttack(target)) {
                performAttack(target)
                attackTimer.reset()
                currentDelay = (1000.0 / effectiveCPS + (Math.random() * (1000.0 / effectiveMinCPS - 1000.0 / effectiveCPS))).toInt()
            }
        }
    }

    private fun selectTarget() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return
        
        val eyes = player.eyes
        val currentRotation = Vec3(
            -Math.sin(Math.toRadians(player.rotationYaw.toDouble())) * Math.cos(Math.toRadians(player.rotationPitch.toDouble())),
            -Math.sin(Math.toRadians(player.rotationPitch.toDouble())),
            Math.cos(Math.toRadians(player.rotationYaw.toDouble())) * Math.cos(Math.toRadians(player.rotationPitch.toDouble()))
        )
        
        var bestTarget: EntityLivingBase? = null
        var bestValue: Double? = null
        
        for (entity in world.loadedEntityList) {
            if (entity !is EntityLivingBase || entity === player) continue
            if (entity is EntityArmorStand) continue
            if (!entity.isEntityAlive) continue
            
            val distance = player.getDistanceToEntityBox(entity)
            if (distance > range) continue
            
            // FOV check
            if (fov < 180f) {
                val toEntity = Vec3(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize()
                val dot = currentRotation.dotProduct(toEntity)
                val angle = Math.toDegrees(Math.acos(dot.coerceIn(-1.0, 1.0)))
                if (angle > fov / 2) continue
            }
            
            val value = when (priority.lowercase()) {
                "distance" -> distance.toDouble()
                "health" -> entity.health.toDouble()
                "angle" -> {
                    val toEntity = Vec3(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize()
                    val dot = currentRotation.dotProduct(toEntity)
                    -(dot + 1.0)
                }
                else -> distance.toDouble()
            }
            
            if (bestValue == null || value < bestValue) {
                bestValue = value
                bestTarget = entity
            }
        }
        
        currentTarget = bestTarget
    }

    private fun performAttack(target: EntityLivingBase) {
        val player = mc.thePlayer ?: return
        
        // Handle W-Tap
        if (attackMode == "WTap" && wTapTimer.hasTimePassed(wTapDelay.toLong())) {
            performWTap()
            wTapTimer.reset()
        }
        
        // Handle Critical Hits
        if (shouldCrit()) {
            performCrit()
        }
        
        // Send attack packet
        sendPacket(C0APacketAnimation())
        
        // Attack the entity via mixin
        player.attackTargetEntityWithCurrentItem(target)
        
        // Reset sprint for next hit
        if (attackMode == "WTap") {
            player.isSprinting = true
            player.serverSprintState = true
        }
        
        // Record attack
        targetHurtInfo[target.entityId] = HurtInfo(
            hitTime = System.currentTimeMillis(),
            hurtTimeRemaining = target.hurtTime
        )
    }

    private fun performWTap() {
        val player = mc.thePlayer ?: return
        
        when (wTapMode.lowercase()) {
            "send" -> {
                sendPackets(
                    C0BPacketEntityAction(player, STOP_SPRINTING),
                    C0BPacketEntityAction(player, START_SPRINTING)
                )
                player.isSprinting = true
                player.serverSprintState = true
            }
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
        }
    }

    private fun performCrit() {
        val player = mc.thePlayer ?: return
        
        if (player.onGround && (lastCritJump == 0 || player.ticksExisted - lastCritJump > 2)) {
            player.jump()
            lastCritJump = player.ticksExisted
            wasInAir = true
        }
    }

    private fun shouldCrit(): Boolean {
        if (!autoCrits && !autoJump) return false
        
        return when (attackMode.lowercase()) {
            "criticals" -> true
            "hybrid" -> autoCrits && Math.random() * 100 < critChance
            else -> false
        }
    }

    private fun canAttack(target: EntityLivingBase): Boolean {
        if (!waitForInvulnerability) return true
        
        val info = targetHurtInfo[target.entityId] ?: return true
        
        val currentTime = System.currentTimeMillis()
        val timeSinceHit = currentTime - info.hitTime
        
        return timeSinceHit >= invulnerabilityWaitTime.toLong()
    }

    private fun updateTargetHurtStates() {
        val currentTime = System.currentTimeMillis()
        val world = mc.theWorld ?: return
        
        // Remove stale entries
        targetHurtInfo.entries.removeIf { (entityId, _) ->
            world.loadedEntityList.none { it.entityId == entityId }
        }
        
        // Update current hurt times
        for (entity in world.loadedEntityList) {
            if (entity !is EntityPlayer) continue
            
            val hurtTime = entity.hurtTime
            val existingInfo = targetHurtInfo[entity.entityId]
            
            if (hurtTime > 0) {
                if (existingInfo == null || existingInfo.hitTime < currentTime - 50) {
                    targetHurtInfo[entity.entityId] = HurtInfo(
                        hitTime = currentTime,
                        hurtTimeRemaining = hurtTime
                    )
                }
            }
        }
    }

    private fun getTargetHurtState(target: EntityLivingBase?): HurtState {
        if (target == null) return HurtState.NONE
        
        val info = targetHurtInfo[target.entityId] ?: return HurtState.NONE
        
        val currentTime = System.currentTimeMillis()
        val timeSinceHit = currentTime - info.hitTime
        
        return when {
            timeSinceHit < invulnerabilityWaitTime.toLong() -> HurtState.INVULNERABLE
            timeSinceHit < invulnerabilityWaitTime.toLong() + 100 -> HurtState.COOLDOWN
            else -> HurtState.READY
        }
    }

    val onRender3D = handler<Render3DEvent> {
        val target = currentTarget ?: return@handler
        
        // Could add target ESP here
    }

    val onRender2D = handler<Render2DEvent> {
        val target = currentTarget ?: return@handler
        
        val state = getTargetHurtState(target)
        val targetHealth = target.health
        val distance = mc.thePlayer?.getDistanceToEntityBox(target)?.let { String.format("%.1f", it) } ?: "?"
        
        val stateColor = when (state) {
            HurtState.READY -> Color.GREEN.rgb
            HurtState.COOLDOWN -> Color.YELLOW.rgb
            HurtState.INVULNERABLE -> Color.RED.rgb
            HurtState.NONE -> Color.GRAY.rgb
        }
        
        val stateText = when (state) {
            HurtState.READY -> "READY"
            HurtState.COOLDOWN -> "COOLDOWN"
            HurtState.INVULNERABLE -> "INVULNERABLE"
            HurtState.NONE -> "NO DATA"
        }
        
        Fonts.font40.drawStringWithShadow("§6[SmartKillAura]", 5f, 5f, Color.ORANGE.rgb)
        Fonts.font40.drawStringWithShadow("§eMode: §f${attackMode}", 5f, 20f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eTarget: §f${target.name}", 5f, 35f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eHealth: §f${targetHealth.toInt()} HP", 5f, 50f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eDistance: §f${distance}", 5f, 65f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow("§eState: ", 5f, 80f, Color.WHITE.rgb)
        Fonts.font40.drawStringWithShadow(stateText, 65f, 80f, stateColor)
        
        if (lowHealthBoost && targetHealth <= healthThreshold) {
            Fonts.font40.drawStringWithShadow("§c[!] LOW HEALTH - AGGRESSIVE", 5f, 95f, Color.RED.rgb)
        }
    }

    val onWorld = handler<WorldEvent> {
        targetHurtInfo.clear()
        currentTarget = null
        attackTimer.reset()
    }

    override val tag
        get() = attackMode
}
