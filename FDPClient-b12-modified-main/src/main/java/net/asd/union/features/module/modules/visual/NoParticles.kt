/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.boolean
import net.asd.union.event.PacketEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.network.play.server.S2APacketParticles
import org.lwjgl.input.Keyboard

/**
 * NoParticles module blocks particle rendering to improve performance during combat.
 * This is especially useful when using KillAura or other combat modules that generate
 * many hit particles, which can cause significant lag.
 * 
 * @author opZywl
 */
object NoParticles : Module("NoParticles", Category.VISUAL, Keyboard.KEY_NONE, defaultInArray = true) {

    // Master switch - blocks ALL particles when enabled
    private val allParticles by boolean("AllParticles", true)
    
    // Performance optimization - set game particle setting to minimal
    private val optimizeGameSettings by boolean("OptimizeGameSettings", true)
    
    // Combat-related particles (most important for performance)
    private val hitParticles by boolean("HitParticles", true) { !allParticles }
    private val critParticles by boolean("CritParticles", true) { !allParticles }
    private val enchantmentParticles by boolean("EnchantmentParticles", true) { !allParticles }
    private val sweepParticles by boolean("SweepParticles", true) { !allParticles }
    
    // Environmental particles
    private val explosionParticles by boolean("ExplosionParticles", true) { !allParticles }
    private val fireParticles by boolean("FireParticles", true) { !allParticles }
    private val waterParticles by boolean("WaterParticles", true) { !allParticles }
    private val smokeParticles by boolean("SmokeParticles", true) { !allParticles }
    private val portalParticles by boolean("PortalParticles", true) { !allParticles }
    private val redstoneParticles by boolean("RedstoneParticles", true) { !allParticles }
    private val noteParticles by boolean("NoteParticles", true) { !allParticles }
    
    // Block-related particles
    private val blockBreakParticles by boolean("BlockBreakParticles", true) { !allParticles }
    private val blockDustParticles by boolean("BlockDustParticles", true) { !allParticles }
    
    // Entity particles
    private val mobParticles by boolean("MobParticles", true) { !allParticles }
    private val villagerParticles by boolean("VillagerParticles", true) { !allParticles }
    
    // Weather particles
    private val rainParticles by boolean("RainParticles", false) { !allParticles }
    private val snowParticles by boolean("SnowParticles", false) { !allParticles }

    // Store original particle setting to restore later
    private var originalParticleSetting = 0

    override fun onEnable() {
        if (optimizeGameSettings) {
            // Store original setting and set to minimal
            originalParticleSetting = mc.gameSettings.particleSetting
            mc.gameSettings.particleSetting = 0 // 0 = Minimal, 1 = Decreased, 2 = All
        }
    }

    override fun onDisable() {
        if (optimizeGameSettings) {
            // Restore original particle setting
            mc.gameSettings.particleSetting = originalParticleSetting
        }
    }

    /**
     * Intercept particle packets and cancel them based on settings
     */
    val onPacket = handler<PacketEvent> { event ->
        if (event.packet is S2APacketParticles) {
            val packet = event.packet
            
            // Block all particles if master switch is enabled
            if (allParticles) {
                event.cancelEvent()
                return@handler
            }
            
            // Block specific particle types based on their names
            val particleName = try {
                packet.particleType.particleName.lowercase()
            } catch (e: Exception) {
                // Fallback if particleName is not available
                packet.particleType.name.lowercase()
            }
            
            when {
                // Combat particles (highest priority for performance)
                (hitParticles && (particleName.contains("iconcrack") || 
                                 particleName.contains("blockcrack") || 
                                 particleName.contains("blockdust") ||
                                 particleName.contains("damage"))) -> {
                    event.cancelEvent()
                }
                
                (critParticles && (particleName.contains("crit") || 
                                  particleName.contains("magiccrit"))) -> {
                    event.cancelEvent()
                }
                
                (sweepParticles && particleName.contains("sweep")) -> {
                    event.cancelEvent()
                }
                
                // Enchantment particles
                (enchantmentParticles && (particleName.contains("mobspell") || 
                                         particleName.contains("spell") || 
                                         particleName.contains("instantspell") || 
                                         particleName.contains("witchmagic") ||
                                         particleName.contains("enchantmenttable"))) -> {
                    event.cancelEvent()
                }
                
                // Explosion particles
                (explosionParticles && (particleName.contains("explode") || 
                                       particleName.contains("largeexplode") || 
                                       particleName.contains("hugeexplosion") || 
                                       particleName.contains("fireworksspark"))) -> {
                    event.cancelEvent()
                }
                
                // Fire particles
                (fireParticles && (particleName.contains("flame") || 
                                  particleName.contains("lava") ||
                                  particleName.contains("driplava"))) -> {
                    event.cancelEvent()
                }
                
                // Water particles
                (waterParticles && (particleName.contains("splash") || 
                                   particleName.contains("wake") || 
                                   particleName.contains("droplet") || 
                                   particleName.contains("dripwater") ||
                                   particleName.contains("bubble"))) -> {
                    event.cancelEvent()
                }
                
                // Smoke particles
                (smokeParticles && (particleName.contains("smoke") || 
                                   particleName.contains("largesmoke") || 
                                   particleName.contains("cloud"))) -> {
                    event.cancelEvent()
                }
                
                // Portal particles
                (portalParticles && (particleName.contains("portal") || 
                                    particleName.contains("endrod"))) -> {
                    event.cancelEvent()
                }
                
                // Redstone particles
                (redstoneParticles && (particleName.contains("reddust") || 
                                      particleName.contains("redstone"))) -> {
                    event.cancelEvent()
                }
                
                // Note particles
                (noteParticles && particleName.contains("note")) -> {
                    event.cancelEvent()
                }
                
                // Block particles
                (blockBreakParticles && particleName.contains("blockcrack")) -> {
                    event.cancelEvent()
                }
                
                (blockDustParticles && particleName.contains("blockdust")) -> {
                    event.cancelEvent()
                }
                
                // Mob particles
                (mobParticles && (particleName.contains("mobspell") || 
                                 particleName.contains("angryvillager") ||
                                 particleName.contains("heart"))) -> {
                    event.cancelEvent()
                }
                
                // Villager particles
                (villagerParticles && (particleName.contains("happyvillager") || 
                                      particleName.contains("angryvillager"))) -> {
                    event.cancelEvent()
                }
                
                // Weather particles
                (rainParticles && particleName.contains("rain")) -> {
                    event.cancelEvent()
                }
                
                (snowParticles && (particleName.contains("snow") || 
                                  particleName.contains("snowshovel"))) -> {
                    event.cancelEvent()
                }
            }
        }
    }
}