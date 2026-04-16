/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.config.int
import net.asd.union.event.EventState
import net.asd.union.event.MotionEvent
import net.asd.union.event.Render2DEvent
import net.asd.union.event.Render3DEvent
import net.asd.union.event.WorldEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.client.TargetModule
import net.asd.union.features.module.modules.client.Teams
import net.asd.union.features.module.modules.other.LinkBots
import net.asd.union.handler.combat.CombatManager.isFocusEntity
import net.asd.union.handler.sessiontabs.SessionRuntimeScope
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.extensions.center
import net.asd.union.utils.extensions.eyes
import net.asd.union.utils.extensions.hitBox
import net.asd.union.utils.extensions.isAnimal
import net.asd.union.utils.extensions.isClientFriend
import net.asd.union.utils.extensions.isMob
import net.asd.union.utils.render.RenderUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.minecraft.world.World
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object KillAuraTargeter : Module("KillAuraTargeter", Category.COMBAT, Keyboard.KEY_NONE) {

    private val renderEspValue by boolean("RenderESP", true)
    private val renderStyleValue by choices("RenderStyle", arrayOf("Outline", "TracerBox"), "TracerBox") { renderEspValue }
    private val showHitbox by boolean("Show Hitbox", false)
    private val hitThroughEntitiesValue by boolean("HitThroughEntities", true)
    private val respectFriendsValue by boolean("RespectFriends", true)
    private val mode by choices("Mode", arrayOf("Normal", "Link Bots"), "Normal") { LinkBots.isLinkedControlActive() }
    private val setTargetMode by choices("SetTargetToView", arrayOf("Once", "Always"), "Once")
    private val updateIntervalTicks by int("UpdateIntervalTicks", 2, 1..20) { setTargetMode == "Always" }
    private val targetSelectionMode by choices(
        "TargetSelectionMode",
        arrayOf("Touching front most hitbox", "Closest Direction based on FOV"),
        "Touching front most hitbox"
    )
    private val maxFOV by float("MaxFOV", 90f, 10f..180f) { targetSelectionMode == "Closest Direction based on FOV" }
    private val throughWalls by boolean("ThroughWalls", true)
    private val maxRange by float("MaxRange", 10f, 3f..20f)
    private val hitboxMultiplier by float("HitboxMultiplier", 1.1f, 1f..20f)

    private var targetEntity: EntityLivingBase? = null
    private var targetName: String = ""
    private var emptyState: Boolean = false

    private var updateTickCounter = 0
    private var infoTickCounter = 0
    private var lastRenderInfo: Triple<String, String, String> = Triple("None", "Unable To determine", "0")

    private data class LinkedTargetDescriptor(
        val entityId: Int,
        val uuid: String?,
        val name: String
    )

    override fun onEnable() {
        resetState(clearInfo = true)

        if (setTargetMode == "Once") {
            updateTarget()
        }
    }

    override fun onDisable() {
        resetState(clearInfo = true)
    }

    val onWorld = handler<WorldEvent> {
        clearTarget()
    }

    val onMotion = handler<MotionEvent> { event ->
        if (!state || event.eventState != EventState.POST) return@handler

        validateCurrentTarget()

        if (setTargetMode == "Always") {
            updateTickCounter++

            val shouldUpdate = targetEntity == null || updateTickCounter >= updateIntervalTicks
            if (shouldUpdate) {
                updateTickCounter = 0
                updateTarget()
            }
        }
    }

    val onRender2D = handler<Render2DEvent> {
        if (!state) return@handler

        infoTickCounter++
        if (infoTickCounter >= 20) {
            infoTickCounter = 0

            val target = targetEntity
            if (target != null && target.isEntityAlive) {
                val health = target.health.roundToInt().toString()
                val distance = (mc.thePlayer?.let { it.getDistanceToEntity(target).roundToInt() } ?: 0).toString()
                lastRenderInfo = Triple(target.name, health, distance)
            } else {
                lastRenderInfo = Triple("None", "Unable To determine", "0")
            }
        }

        val font = Fonts.font40
        var posX = 5f
        var posY = 5f
        val lineHeight = font.FONT_HEIGHT + 2

        font.drawStringWithShadow("Attacking Entity: §6${lastRenderInfo.first}", posX, posY, Color.WHITE.rgb)
        posY += lineHeight.toFloat()
        font.drawStringWithShadow("Approximated Health of Entity: §6${lastRenderInfo.second}", posX, posY, Color.WHITE.rgb)
        posY += lineHeight.toFloat()
        font.drawStringWithShadow("Distance of Entity to Self: §6${lastRenderInfo.third}", posX, posY, Color.WHITE.rgb)
    }

    val onRender3D = handler<Render3DEvent>(priority = -10) { event ->
        if (!state || (!renderEspValue && !showHitbox)) return@handler

        val entity = targetEntity ?: return@handler
        if (!entity.isEntityAlive) return@handler

        val selectionBox = expandHitbox(entity.hitBox)
        val renderBox = if (showHitbox) selectionBox else entity.hitBox

        val partial = event.partialTicks.toDouble()
        val interpX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial
        val interpY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial
        val interpZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial

        val offsetX = interpX - entity.posX - mc.renderManager.renderPosX
        val offsetY = interpY - entity.posY - mc.renderManager.renderPosY
        val offsetZ = interpZ - entity.posZ - mc.renderManager.renderPosZ

        val adjustedBox = renderBox.offset(offsetX, offsetY, offsetZ)
        val renderTracerBox = renderStyleValue == "TracerBox"

        glPushMatrix()
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        glColor4f(1f, 0f, 0f, 1f)
        glLineWidth(2f)
        RenderUtils.drawSelectionBoundingBox(adjustedBox)

        if (renderTracerBox) {
            glColor4f(1f, 0f, 0f, 0.18f)
            RenderUtils.drawFilledBox(adjustedBox)
            drawTargetTracer(entity, event.partialTicks)
        }

        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)

        glPopAttrib()
        glPopMatrix()
    }

    private fun updateTarget() {
        val player = mc.thePlayer ?: run {
            clearTarget()
            return
        }
        val world = mc.theWorld ?: run {
            clearTarget()
            return
        }

        val eyes = player.eyes
        val lookVec = normalizedLookVector(player.rotationYaw, player.rotationPitch) ?: run {
            clearTarget()
            return
        }

        val range = maxRange.toDouble()
        val maxRangeSq = range * range
        val maxFovCos = cos(Math.toRadians(maxFOV.toDouble()))
        val rayEnd = Vec3(
            eyes.xCoord + lookVec.xCoord * range,
            eyes.yCoord + lookVec.yCoord * range,
            eyes.zCoord + lookVec.zCoord * range
        )

        var bestEntity: EntityLivingBase? = null
        var bestDistance = Double.MAX_VALUE
        var bestCos = -2.0

        for (raw in world.loadedEntityList) {
            val entity = raw as? EntityLivingBase ?: continue
            if (!isEntityCandidate(player, entity)) continue

            when (targetSelectionMode) {
                "Touching front most hitbox" -> {
                    val expandedBox = expandHitbox(entity.hitBox)
                    val hitInfo = getRayHitInfo(eyes, rayEnd, expandedBox) ?: continue

                    if (!throughWalls && !hasLineOfSight(eyes, hitInfo.hitVec)) {
                        continue
                    }

                    if (hitInfo.distanceSq < bestDistance) {
                        bestEntity = entity
                        bestDistance = hitInfo.distanceSq
                    }
                }

                else -> {
                    val center = entity.hitBox.center
                    val centerDirX = center.xCoord - eyes.xCoord
                    val centerDirY = center.yCoord - eyes.yCoord
                    val centerDirZ = center.zCoord - eyes.zCoord
                    val centerDistSq = centerDirX * centerDirX + centerDirY * centerDirY + centerDirZ * centerDirZ
                    if (centerDistSq > maxRangeSq) continue

                    val centerCos = if (centerDistSq < 1.0E-8) {
                        1.0
                    } else {
                        (lookVec.xCoord * centerDirX + lookVec.yCoord * centerDirY + lookVec.zCoord * centerDirZ) / sqrt(centerDistSq)
                    }

                    if (centerCos < maxFovCos) continue
                    if (!throughWalls && !hasLineOfSight(eyes, center)) continue

                    if (centerCos > bestCos + 1.0E-9 || (abs(centerCos - bestCos) <= 1.0E-9 && centerDistSq < bestDistance)) {
                        bestEntity = entity
                        bestCos = centerCos
                        bestDistance = centerDistSq
                    }
                }
            }
        }

        bestEntity?.let(::setTarget) ?: clearTarget()
    }

    private fun validateCurrentTarget() {
        val target = targetEntity ?: return
        val player = mc.thePlayer ?: run {
            clearTarget()
            return
        }

        if (!isEntityCandidate(player, target)) {
            clearTarget()
            return
        }

        val eyes = player.eyes
        val lookVec = normalizedLookVector(player.rotationYaw, player.rotationPitch) ?: run {
            clearTarget()
            return
        }

        val range = maxRange.toDouble()
        val maxRangeSq = range * range
        val maxFovCos = cos(Math.toRadians(maxFOV.toDouble()))
        val expandedBox = expandHitbox(target.hitBox)

        val rayEnd = Vec3(
            eyes.xCoord + lookVec.xCoord * range,
            eyes.yCoord + lookVec.yCoord * range,
            eyes.zCoord + lookVec.zCoord * range
        )

        when (targetSelectionMode) {
            "Touching front most hitbox" -> {
                val hitInfo = getRayHitInfo(eyes, rayEnd, expandedBox)
                if (hitInfo == null) {
                    clearTarget()
                    return
                }

                if (!throughWalls && !hasLineOfSight(eyes, hitInfo.hitVec)) {
                    clearTarget()
                }
            }

            else -> {
                val center = target.hitBox.center
                val centerDirX = center.xCoord - eyes.xCoord
                val centerDirY = center.yCoord - eyes.yCoord
                val centerDirZ = center.zCoord - eyes.zCoord
                val centerDistSq = centerDirX * centerDirX + centerDirY * centerDirY + centerDirZ * centerDirZ

                if (centerDistSq > maxRangeSq) {
                    clearTarget()
                    return
                }

                if (centerDistSq < 1.0E-8) {
                    if (!throughWalls && !hasLineOfSight(eyes, center)) {
                        clearTarget()
                    }
                    return
                }

                val dot = lookVec.xCoord * centerDirX + lookVec.yCoord * centerDirY + lookVec.zCoord * centerDirZ
                if (dot <= 0.0) {
                    clearTarget()
                    return
                }

                val cosAngle = dot / sqrt(centerDistSq)
                if (cosAngle < maxFovCos) {
                    clearTarget()
                    return
                }

                if (!throughWalls && !hasLineOfSight(eyes, center)) {
                    clearTarget()
                }
            }
        }
    }

    private fun isEntityCandidate(player: EntityPlayer, entity: EntityLivingBase): Boolean {
        if (entity == player || !entity.isEntityAlive) return false

        if (!TargetModule.invisibleValue && entity.isInvisible) return false

        val typeAllowed = when (entity) {
            is EntityPlayer -> TargetModule.playerValue
            else -> (TargetModule.mobValue && entity.isMob()) || (TargetModule.animalValue && entity.isAnimal())
        }
        if (!typeAllowed) return false

        if (entity is EntityPlayer) {
            if (entity.isSpectator) return false
            if (respectFriendsValue && entity.isClientFriend()) return false
            if (!isFocusEntity(entity)) return false
            if (Teams.handleEvents() && Teams.isInYourTeam(entity)) return false
        }

        return true
    }

    private fun setTarget(entity: EntityLivingBase) {
        targetEntity = entity
        targetName = if (entity is EntityPlayer) entity.name else entity.javaClass.simpleName
        emptyState = false
    }

    private fun clearTarget() {
        targetEntity = null
        targetName = ""
        emptyState = true
    }

    private fun resetState(clearInfo: Boolean) {
        targetEntity = null
        targetName = ""
        emptyState = false
        updateTickCounter = 0

        if (clearInfo) {
            infoTickCounter = 0
            lastRenderInfo = Triple("None", "Unable To determine", "0")
        }
    }

    private fun expandHitbox(box: AxisAlignedBB): AxisAlignedBB {
        if (hitboxMultiplier <= 1.00001f) {
            return box
        }

        val multiplierDelta = hitboxMultiplier.toDouble() - 1.0
        val widthExpand = (box.maxX - box.minX) * multiplierDelta * 0.5
        val heightExpand = (box.maxY - box.minY) * multiplierDelta * 0.5
        val depthExpand = (box.maxZ - box.minZ) * multiplierDelta * 0.5

        return AxisAlignedBB.fromBounds(
            box.minX - widthExpand,
            box.minY - heightExpand,
            box.minZ - depthExpand,
            box.maxX + widthExpand,
            box.maxY + heightExpand,
            box.maxZ + depthExpand
        )
    }

    private fun normalizedLookVector(yaw: Float, pitch: Float): Vec3? {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val raw = Vec3(
            -sin(yawRad) * cos(pitchRad),
            -sin(pitchRad),
            cos(yawRad) * cos(pitchRad)
        )

        val len = raw.lengthVector()
        return if (len < 1.0E-8) null else Vec3(raw.xCoord / len, raw.yCoord / len, raw.zCoord / len)
    }

    private fun drawTargetTracer(entity: EntityLivingBase, partialTicks: Float) {
        val player = mc.thePlayer ?: return

        val partial = partialTicks.toDouble()
        val interpX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial
        val interpY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial
        val interpZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial

        val targetX = interpX - mc.renderManager.renderPosX
        val targetY = interpY + entity.height.toDouble() * 0.5 - mc.renderManager.renderPosY
        val targetZ = interpZ - mc.renderManager.renderPosZ

        val yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks
        val pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks

        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val eyeX = -sin(yawRad) * cos(pitchRad)
        val eyeY = -sin(pitchRad) + player.eyeHeight.toDouble()
        val eyeZ = cos(yawRad) * cos(pitchRad)

        glColor4f(1f, 0f, 0f, 0.95f)
        glLineWidth(2.2f)
        glBegin(GL_LINES)
        glVertex3d(eyeX, eyeY, eyeZ)
        glVertex3d(targetX, targetY, targetZ)
        glEnd()
    }

    private fun getRayHitInfo(eyes: Vec3, rayEnd: Vec3, box: AxisAlignedBB): RayHitInfo? {
        if (box.isVecInside(eyes)) {
            return RayHitInfo(0.0, eyes)
        }

        val intercept = box.calculateIntercept(eyes, rayEnd) ?: return null
        return RayHitInfo(eyes.squareDistanceTo(intercept.hitVec), intercept.hitVec)
    }

    private fun hasLineOfSight(eyes: Vec3, point: Vec3): Boolean {
        val world = mc.theWorld ?: return false
        val trace = world.rayTraceBlocks(eyes, point, false, true, false)
        return trace == null || trace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
    }

    private fun nearestPointOnAabb(point: Vec3, box: AxisAlignedBB): Vec3 {
        val x = clamp(point.xCoord, box.minX, box.maxX)
        val y = clamp(point.yCoord, box.minY, box.maxY)
        val z = clamp(point.zCoord, box.minZ, box.maxZ)
        return Vec3(x, y, z)
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    private fun distanceSqToAabb(point: Vec3, box: AxisAlignedBB): Double {
        val dx = when {
            point.xCoord < box.minX -> box.minX - point.xCoord
            point.xCoord > box.maxX -> point.xCoord - box.maxX
            else -> 0.0
        }
        val dy = when {
            point.yCoord < box.minY -> box.minY - point.yCoord
            point.yCoord > box.maxY -> point.yCoord - box.maxY
            else -> 0.0
        }
        val dz = when {
            point.zCoord < box.minZ -> box.minZ - point.zCoord
            point.zCoord > box.maxZ -> point.zCoord - box.maxZ
            else -> 0.0
        }
        return dx * dx + dy * dy + dz * dz
    }

    fun isTargetSelected(): Boolean {
        return getTargetEntity() != null && targetName.isNotEmpty()
    }

    fun isEmpty(): Boolean = emptyState

    fun isLinkBotsModeActive(): Boolean {
        return state && mode.equals("Link Bots", ignoreCase = true) && LinkBots.isLinkedControlActive()
    }

    fun getTargetEntity(): EntityLivingBase? {
        if (SessionRuntimeScope.isDetachedContextActive()) {
            linkedTargetDescriptor()?.let { descriptor ->
                return resolveLinkedTarget(mc.theWorld, descriptor)
            }
        }

        val target = targetEntity ?: return null
        if (!target.isEntityAlive) {
            clearTarget()
            return null
        }
        return target
    }

    fun getTargetPlayer(): EntityPlayer? = getTargetEntity() as? EntityPlayer

    fun getTargetName(): String = targetName

    private fun linkedTargetDescriptor(): LinkedTargetDescriptor? {
        if (!isLinkBotsModeActive()) {
            return null
        }

        val target = targetEntity ?: return null
        if (!target.isEntityAlive) {
            clearTarget()
            return null
        }

        return LinkedTargetDescriptor(
            entityId = target.entityId,
            uuid = (target as? EntityPlayer)?.uniqueID?.toString(),
            name = target.name
        )
    }

    private fun resolveLinkedTarget(world: World?, descriptor: LinkedTargetDescriptor): EntityLivingBase? {
        val currentWorld = world ?: return null

        val directHit = currentWorld.getEntityByID(descriptor.entityId) as? EntityLivingBase
        if (matchesLinkedTarget(directHit, descriptor)) {
            return directHit
        }

        return currentWorld.loadedEntityList
            .asSequence()
            .mapNotNull { it as? EntityLivingBase }
            .firstOrNull { matchesLinkedTarget(it, descriptor) }
    }

    private fun matchesLinkedTarget(entity: EntityLivingBase?, descriptor: LinkedTargetDescriptor): Boolean {
        if (entity == null || !entity.isEntityAlive) {
            return false
        }

        if (entity.entityId == descriptor.entityId) {
            return true
        }

        if (entity is EntityPlayer && descriptor.uuid != null && entity.uniqueID.toString().equals(descriptor.uuid, ignoreCase = true)) {
            return true
        }

        return entity.name.equals(descriptor.name, ignoreCase = true)
    }

    fun shouldBlockOtherTargets(): Boolean = state && isTargetSelected()

    fun shouldAllowThroughEntities(entity: Entity?): Boolean {
        return state && hitThroughEntitiesValue && entity != null && entity == targetEntity
    }

    private data class RayHitInfo(val distanceSq: Double, val hitVec: Vec3)
}
