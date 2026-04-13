/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.IntegerValue
import net.asd.union.config.boolean
import net.asd.union.config.float
import net.asd.union.config.int
import net.asd.union.event.*
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.MinecraftInstance.Companion.mc
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.input.Keyboard
import java.awt.Color

object SmartKillAura : Module("SmartKillAura", Category.COMBAT, Keyboard.KEY_NONE, hideModule = false) {

    private var previousKillAuraHurtTime: Int? = null
    private var killAuraWasEnabled = false
    private var cachedKillAuraHurtTimeValue: IntegerValue? = null
    private var previousMaxCPS: Int? = null
    private var previousMinCPS: Int? = null

    private val strictMode by boolean("StrictMode", true, subjective = true)
    private val waitForInvulnerability by boolean("WaitForInvulnerability", true, subjective = true)
    private val invulnerabilityWaitTime by int("InvulnerabilityWaitTime", 250, 100..500, subjective = true) { waitForInvulnerability }
    private val dynamicDamage by boolean("DynamicDamage", true, subjective = true)
    private val minDamageThreshold by float("MinDamageThreshold", 0.7f, 0.0f..1.0f, subjective = true) { dynamicDamage }
    private val optimalCPSTiming by boolean("OptimalCPSTiming", true, subjective = true)
    private val optimalMaxCPS by int("OptimalMaxCPS", 2, 1..10, subjective = true) { optimalCPSTiming }
    private val optimalMinCPS by int("OptimalMinCPS", 2, 1..10, subjective = true) { optimalCPSTiming }
    private val debugInfo by boolean("DebugInfo", false, subjective = true)

    private val targetHurtInfo = mutableMapOf<Int, HurtInfo>()

    private data class HurtInfo(
        val hitTime: Long,
        val hurtTimeRemaining: Int
    )

    private fun killAuraHurtTimeValue(): IntegerValue? {
        cachedKillAuraHurtTimeValue?.let { return it }
        val resolved = KillAura.getValue("HurtTime") as? IntegerValue
        cachedKillAuraHurtTimeValue = resolved
        return resolved
    }

    private fun enforceStrictHurtTime() {
        killAuraHurtTimeValue()?.let { hurtTime ->
            if (hurtTime.get() != 0) {
                hurtTime.set(0)
            }
        }
    }

    private fun applyOptimalCPS() {
        if (!state || !strictMode || !optimalCPSTiming) return
        
        try {
            val maxCPSValue = KillAura.getValue("MaxCPS") as? IntegerValue
            val minCPSValue = KillAura.getValue("MinCPS") as? IntegerValue
            
            if (previousMaxCPS == null) {
                previousMaxCPS = maxCPSValue?.get() ?: 8
            }
            if (previousMinCPS == null) {
                previousMinCPS = minCPSValue?.get() ?: 5
            }
            
            maxCPSValue?.set(optimalMaxCPS)
            minCPSValue?.set(optimalMinCPS)
        } catch (_: Exception) {
        }
    }

    private fun restoreOriginalCPS() {
        try {
            val maxCPSValue = KillAura.getValue("MaxCPS") as? IntegerValue
            val minCPSValue = KillAura.getValue("MinCPS") as? IntegerValue
            
            previousMaxCPS?.let { maxCPSValue?.set(it) }
            previousMinCPS?.let { minCPSValue?.set(it) }
            
            previousMaxCPS = null
            previousMinCPS = null
        } catch (_: Exception) {
        }
    }

    private val onUpdate = handler<UpdateEvent> {
        if (!KillAura.state) {
            state = false
            return@handler
        }

        enforceStrictHurtTime()

        if (strictMode) {
            updateTargetHurtStates()
        }

        if (optimalCPSTiming) {
            applyOptimalCPS()
        }
    }

    private fun updateTargetHurtStates() {
        val currentTime = System.currentTimeMillis()
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        targetHurtInfo.entries.removeIf { (entityId, _) ->
            world.loadedEntityList.none { it.entityId == entityId }
        }

        for (entity in world.loadedEntityList) {
            if (entity !is EntityLivingBase || entity === player) continue
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
            } else if (existingInfo != null && currentTime - existingInfo.hitTime > invulnerabilityWaitTime.toLong() + 200) {
                targetHurtInfo.remove(entity.entityId)
            }
        }
    }

    fun canAttackTarget(target: EntityLivingBase?): Boolean {
        if (target == null) return true
        if (!state || !strictMode || !waitForInvulnerability) return true

        val info = targetHurtInfo[target.entityId] ?: return true

        val currentTime = System.currentTimeMillis()
        val timeSinceHit = currentTime - info.hitTime

        if (timeSinceHit < invulnerabilityWaitTime.toLong()) {
            return false
        }

        return true
    }

    fun getDamageMultiplier(target: EntityLivingBase?): Float {
        if (target == null) return 1.0f
        if (!state || !dynamicDamage) return 1.0f

        val info = targetHurtInfo[target.entityId] ?: return 1.0f

        val currentTime = System.currentTimeMillis()
        val timeSinceHit = currentTime - info.hitTime

        if (timeSinceHit > invulnerabilityWaitTime.toLong() + 100) {
            return 1.0f
        }

        if (timeSinceHit < invulnerabilityWaitTime.toLong()) {
            return 0.0f
        }

        val progress = ((timeSinceHit - invulnerabilityWaitTime.toLong()).toFloat() / 100f).coerceIn(0f, 1f)

        return progress.coerceAtLeast(minDamageThreshold)
    }

    fun getTargetHurtState(target: EntityLivingBase?): HurtState {
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

    fun getTimeUntilAttackable(target: EntityLivingBase?): Long {
        if (target == null) return 0L
        if (!state || !strictMode || !waitForInvulnerability) return 0L

        val info = targetHurtInfo[target.entityId] ?: return 0L

        val currentTime = System.currentTimeMillis()
        val timeSinceHit = currentTime - info.hitTime
        val remaining = invulnerabilityWaitTime.toLong() - timeSinceHit

        return remaining.coerceAtLeast(0L)
    }

    val onRender2D = handler<Render2DEvent> {
        if (!debugInfo || !state) return@handler

        val target = KillAura.target
        val targetState = getTargetHurtState(target)
        val timeUntilAttack = getTimeUntilAttackable(target)

        val stateText = when (targetState) {
            HurtState.NONE -> "Target: No hurt data"
            HurtState.INVULNERABLE -> "Target: INVULNERABLE (${timeUntilAttack}ms)"
            HurtState.COOLDOWN -> "Target: COOLDOWN"
            HurtState.READY -> "Target: READY"
        }

        val cpsText = if (optimalCPSTiming) {
            "CPS: ${optimalMinCPS}-${optimalMaxCPS} (optimal)"
        } else {
            "CPS: Original"
        }

        Fonts.font40.drawStringWithShadow("§6[SmartKillAura]", 5f, 5f, Color.ORANGE.rgb)
        Fonts.font40.drawStringWithShadow(stateText, 5f, 20f, when (targetState) {
            HurtState.READY -> Color.GREEN.rgb
            HurtState.COOLDOWN -> Color.YELLOW.rgb
            HurtState.INVULNERABLE -> Color.RED.rgb
            HurtState.NONE -> Color.GRAY.rgb
        })
        Fonts.font40.drawStringWithShadow(cpsText, 5f, 35f, Color.WHITE.rgb)
    }

    enum class HurtState {
        NONE,
        INVULNERABLE,
        COOLDOWN,
        READY
    }

    override fun onEnable() {
        cachedKillAuraHurtTimeValue = null
        previousKillAuraHurtTime = killAuraHurtTimeValue()?.get()
        killAuraWasEnabled = KillAura.state

        if (!KillAura.state) {
            KillAura.state = true
        }

        enforceStrictHurtTime()
        
        targetHurtInfo.clear()

        if (optimalCPSTiming) {
            applyOptimalCPS()
        }
    }

    override fun onDisable() {
        previousKillAuraHurtTime?.let { original ->
            killAuraHurtTimeValue()?.set(original)
        }

        previousKillAuraHurtTime = null
        cachedKillAuraHurtTimeValue = null

        if (!killAuraWasEnabled && KillAura.state) {
            KillAura.state = false
        }

        killAuraWasEnabled = false
        targetHurtInfo.clear()

        if (optimalCPSTiming) {
            restoreOriginalCPS()
        }
    }

    val onWorld = handler<WorldEvent> {
        targetHurtInfo.clear()
    }
}
