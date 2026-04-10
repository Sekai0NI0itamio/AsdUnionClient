/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.config.boolean
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.combat.KillAuraTargeter
import net.asd.union.file.FileManager.friendsConfig
import net.asd.union.FDPClient
import net.asd.union.utils.client.chat
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.input.Keyboard

/**
 * FriendAdder module automatically adds the currently targeted player to your friends list.
 * Uses KillAuraTargeter to detect which player you're looking at and adds them as a friend.
 * 
 * Keybind: F (default) - Press to instantly add the player you're looking at as a friend
 * 
 * @author opZywl
 */
object FriendAdder : Module("FriendAdder", Category.CLIENT, Keyboard.KEY_F) {

    // Configuration options
    private val autoDisable by boolean("AutoDisable", true)
    private val showMessages by boolean("ShowMessages", true)
    private val requireTargeter by boolean("RequireTargeter", true)

    // Track last added friend to prevent spam
    private var lastAddedFriend: String? = null
    private var lastAddTime = 0L

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        try {
            // Get target from KillAuraTargeter if required and available
            val targetEntity = if (requireTargeter && KillAuraTargeter.state) {
                KillAuraTargeter.getTargetPlayer()
            } else {
                // Fallback: find the closest player the user is looking at
                findPlayerInCrosshair()
            }

            if (targetEntity != null && targetEntity is EntityPlayer) {
                val playerName = targetEntity.name
                
                // Prevent adding the same friend multiple times quickly
                val currentTime = System.currentTimeMillis()
                if (lastAddedFriend == playerName && currentTime - lastAddTime < 2000) {
                    if (showMessages) {
                        chat("§c§lFriendAdder §7» §cAlready tried to add §f$playerName §crecently!")
                    }
                    if (autoDisable) state = false
                    return
                }

                // Don't add yourself as a friend
                if (playerName.equals(player.name, ignoreCase = true)) {
                    if (showMessages) {
                        chat("§c§lFriendAdder §7» §cCannot add yourself as a friend!")
                    }
                    if (autoDisable) state = false
                    return
                }

                // Check if already a friend
                if (friendsConfig.isFriend(playerName)) {
                    if (showMessages) {
                        chat("§c§lFriendAdder §7» §f$playerName §cis already your friend!")
                    }
                    if (autoDisable) state = false
                    return
                }

                // Add the friend
                if (friendsConfig.addFriend(playerName, playerName)) {
                    FDPClient.fileManager.saveConfig(friendsConfig)
                    
                    if (showMessages) {
                        chat("§a§lFriendAdder §7» §aAdded §f$playerName §ato your friends list!")
                    }
                    
                    // Update tracking variables
                    lastAddedFriend = playerName
                    lastAddTime = currentTime
                    
                } else {
                    if (showMessages) {
                        chat("§c§lFriendAdder §7» §cFailed to add §f$playerName §cas a friend!")
                    }
                }
            } else {
                if (showMessages) {
                    val message = if (requireTargeter && !KillAuraTargeter.state) {
                        "§c§lFriendAdder §7» §cKillAuraTargeter is not enabled! Enable it or disable RequireTargeter."
                    } else {
                        "§c§lFriendAdder §7» §cNo player found in your crosshair!"
                    }
                    chat(message)
                }
            }
        } catch (e: Exception) {
            if (showMessages) {
                chat("§c§lFriendAdder §7» §cError: ${e.message}")
            }
        }

        // Auto-disable after use
        if (autoDisable) {
            state = false
        }
    }

    /**
     * Find the player that the user is currently looking at (crosshair targeting)
     * This is a fallback when KillAuraTargeter is not available
     */
    private fun findPlayerInCrosshair(): EntityPlayer? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        var closestPlayer: EntityPlayer? = null
        var closestDistance = Double.MAX_VALUE

        // Get player's look direction
        val playerEyes = player.getPositionEyes(1.0f)
        val lookVec = player.getLook(1.0f)
        val maxDistance = 10.0 // Maximum distance to look for players

        // Extend the look vector
        val lookEnd = playerEyes.addVector(
            lookVec.xCoord * maxDistance,
            lookVec.yCoord * maxDistance,
            lookVec.zCoord * maxDistance
        )

        // Check all players in the world
        for (entity in world.loadedEntityList) {
            if (entity !is EntityPlayer || entity == player) continue

            // Check if the look ray intersects with the player's bounding box
            val intersection = entity.entityBoundingBox.calculateIntercept(playerEyes, lookEnd)
            
            if (intersection != null) {
                val distance = playerEyes.distanceTo(intersection.hitVec)
                
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestPlayer = entity
                }
            }
        }

        return closestPlayer
    }

    // Update handler to show current target info (optional)
    val onUpdate = handler<UpdateEvent> {
        // This could be used to show current target info in the future
        // For now, we keep it minimal to avoid performance impact
    }
}