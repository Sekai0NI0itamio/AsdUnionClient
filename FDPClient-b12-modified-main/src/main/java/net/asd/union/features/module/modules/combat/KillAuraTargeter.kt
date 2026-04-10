/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.config.boolean
import net.asd.union.event.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.extensions.eyes
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.asd.union.utils.extensions.isClientFriend
import net.asd.union.utils.extensions.rotation
import net.asd.union.utils.rotation.RaycastUtils
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.render.RenderUtils
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.*

object KillAuraTargeter : Module("KillAuraTargeter", Category.COMBAT, Keyboard.KEY_NONE) {

    private val renderEspValue by boolean("RenderESP", true)
    private val hitThroughEntitiesValue by boolean("HitThroughEntities", true)
    private val onlyPlayerValue by boolean("OnlyPlayer", true)
    private val respectFriendsValue by boolean("RespectFriends", true)
    private val setTargetMode by choices("SetTargetToView", arrayOf("Once", "Always"), "Once")
    private val maxFOV by float("MaxFOV", 10f, 10f..180f)
    private val throughWalls by boolean("ThroughWalls", true)
    private val maxRange by float("MaxRange", 10f, 3f..20f)
    private val hitboxMultiplier by float("HitboxMultiplier", 1.1f, 1f..20f)

    private var _targetEntity: EntityLivingBase? = null
    private var targetName: String = ""
    private var isEmpty: Boolean = false

    override fun onEnable() {
        _targetEntity = null
        targetName = ""
        isEmpty = false

        // If mode is "Once", set the target immediately upon enabling
        if (setTargetMode == "Once") {
            updateTarget()
        }
    }

    override fun onDisable() {
        _targetEntity = null
        targetName = ""
        isEmpty = false
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState.name == "POST") {
            // Only update target if mode is "Always"
            if (setTargetMode == "Always") {
                updateTarget()
            }
        }
    }

    // Render with low priority to ensure it renders after other ESP modules
    val onRender3D = handler<Render3DEvent>(priority = -10) { event ->
        if (!state || !renderEspValue || _targetEntity == null) return@handler
        
        val entity = _targetEntity ?: return@handler
        
        // Ensure entity is still valid and alive
        if (!entity.isEntityAlive) return@handler

        try {
            glPushMatrix()
            glPushAttrib(GL_ALL_ATTRIB_BITS)
            
            // Disable depth test to render through walls
            glDisable(GL_DEPTH_TEST)
            glDepthMask(false)
            
            // Enable blending for smooth rendering
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            
            // Disable texture and lighting
            glDisable(GL_TEXTURE_2D)
            glDisable(GL_LIGHTING)
            
            // Enable line smoothing for better appearance
            glEnable(GL_LINE_SMOOTH)
            glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)

            // Calculate interpolated entity position
            val x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * event.partialTicks - mc.renderManager.renderPosX
            val y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * event.partialTicks - mc.renderManager.renderPosY
            val z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * event.partialTicks - mc.renderManager.renderPosZ

            val entityBox = entity.entityBoundingBox

            // Create adjusted bounding box
            val adjustedBox = AxisAlignedBB.fromBounds(
                entityBox.minX - entity.posX + x - 0.05,
                entityBox.minY - entity.posY + y,
                entityBox.minZ - entity.posZ + z - 0.05,
                entityBox.maxX - entity.posX + x + 0.05,
                entityBox.maxY - entity.posY + y + 0.15,
                entityBox.maxZ - entity.posZ + z + 0.05
            )

            // Set red color with full opacity
            glColor4f(1.0f, 0.0f, 0.0f, 1.0f)
            
            // Set line width
            glLineWidth(4f)
            
            // Draw the bounding box
            RenderUtils.drawSelectionBoundingBox(adjustedBox)
            
            // Restore GL state
            glEnable(GL_DEPTH_TEST)
            glDepthMask(true)
            glDisable(GL_LINE_SMOOTH)
            glEnable(GL_TEXTURE_2D)
            glDisable(GL_BLEND)
            
            glPopAttrib()
            glPopMatrix()
        } catch (e: Exception) {
            // Silently catch any rendering exceptions to prevent crashes
        }
    }

    private fun updateTarget() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        // Get all loaded entities from the server/world
        val allEntities = world.loadedEntityList
        
        var bestTarget: EntityLivingBase? = null
        var bestDistance = Double.MAX_VALUE

        // Get player's eye position and look direction
        val playerEyes = player.eyes
        val lookVec = getVectorForRotation(player.rotationYaw, player.rotationPitch)

        for (entity in allEntities) {
            // Skip invalid entities
            if (entity !is EntityLivingBase || entity == player || !entity.isEntityAlive) continue
            
            // Skip non-player entities if OnlyPlayer is enabled
            if (onlyPlayerValue && entity !is EntityPlayer) continue
            
            // Skip friends if RespectFriends is enabled
            if (respectFriendsValue && entity is EntityPlayer && entity.isClientFriend()) continue
            
            // Calculate distance from player to entity first (for early filtering)
            val distance = player.getDistanceToEntityBox(entity)
            
            // Skip entities that are too far away
            if (distance > maxRange) continue
            
            // Get entity's bounding box and expand it based on multiplier
            val originalBox = entity.entityBoundingBox
            val boxWidth = originalBox.maxX - originalBox.minX
            val boxHeight = originalBox.maxY - originalBox.minY
            val boxDepth = originalBox.maxZ - originalBox.minZ
            
            // Calculate expansion based on multiplier (multiplier of 1.0 = no change, 2.0 = double size)
            val widthExpansion = (boxWidth * (hitboxMultiplier - 1f)) / 2f
            val heightExpansion = (boxHeight * (hitboxMultiplier - 1f)) / 2f
            val depthExpansion = (boxDepth * (hitboxMultiplier - 1f)) / 2f
            
            val expandedBox = AxisAlignedBB.fromBounds(
                originalBox.minX - widthExpansion,
                originalBox.minY - heightExpansion,
                originalBox.minZ - depthExpansion,
                originalBox.maxX + widthExpansion,
                originalBox.maxY + heightExpansion,
                originalBox.maxZ + depthExpansion
            )
            
            // Check if crosshair ray intersects with the expanded hitbox
            val isLookingAtEntity = isLookingAtHitbox(playerEyes, lookVec, expandedBox, distance)
            
            if (!isLookingAtEntity) continue
            
            // Check visibility if throughWalls is disabled
            if (!throughWalls) {
                val entityCenter = Vec3(
                    entity.posX,
                    entity.posY + entity.eyeHeight / 2.0,
                    entity.posZ
                )
                val raycastResult = world.rayTraceBlocks(playerEyes, entityCenter, false, true, false)
                if (raycastResult != null && raycastResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    continue // Entity is behind a wall
                }
            }
            
            // Select the closest entity that the crosshair is pointing at
            if (distance < bestDistance) {
                bestDistance = distance
                bestTarget = entity
            }
        }

        if (bestTarget != null) {
            _targetEntity = bestTarget
            targetName = when (bestTarget) {
                is EntityPlayer -> bestTarget.name
                else -> bestTarget.javaClass.simpleName
            }
            isEmpty = false
        } else {
            // Only clear target if it's not from "Once" mode (to preserve the initial target)
            if (setTargetMode != "Once" || _targetEntity == null) {
                _targetEntity = null
                targetName = ""
                isEmpty = true  // Set to empty state when no target found
            }
        }
    }
    
    /**
     * Check if the player's crosshair (look direction) intersects with an entity's hitbox
     * This works at any distance by projecting the look ray and checking intersection
     */
    private fun isLookingAtHitbox(eyePos: Vec3, lookDirection: Vec3, hitbox: AxisAlignedBB, maxDistance: Double): Boolean {
        // Normalize the look direction
        val normalizedLook = lookDirection.normalize()
        
        // Create a ray from eye position in the look direction
        // Extend the ray to a reasonable distance (much further than maxDistance to handle long ranges)
        val rayLength = maxOf(maxDistance * 2, 1000.0) // Ensure ray is long enough
        val rayEnd = eyePos.addVector(
            normalizedLook.xCoord * rayLength,
            normalizedLook.yCoord * rayLength,
            normalizedLook.zCoord * rayLength
        )
        
        // Use Minecraft's built-in ray-box intersection
        val intersection = hitbox.calculateIntercept(eyePos, rayEnd)
        
        // If intersection is not null, the ray hits the hitbox
        return intersection != null
    }
    
    /**
     * Get normalized look vector from yaw and pitch
     */
    private fun getVectorForRotation(yaw: Float, pitch: Float): Vec3 {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)
        
        return Vec3(x, y, z)
    }

    fun isTargetSelected(): Boolean {
        return _targetEntity != null && targetName.isNotEmpty()
    }

    fun isEmpty(): Boolean {
        return isEmpty
    }

    fun getTargetEntity(): EntityLivingBase? {
        return _targetEntity
    }

    fun getTargetPlayer(): EntityPlayer? {
        return _targetEntity as? EntityPlayer
    }

    fun getTargetName(): String {
        return targetName
    }

    fun shouldBlockOtherTargets(): Boolean {
        return state && isTargetSelected()
    }

    fun shouldAllowThroughEntities(entity: Entity?): Boolean {
        return state && hitThroughEntitiesValue && entity == _targetEntity
    }
}