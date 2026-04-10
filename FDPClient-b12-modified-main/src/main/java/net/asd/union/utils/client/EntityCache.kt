/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.client

import net.asd.union.event.Listenable
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.modules.client.TargetModule
import net.asd.union.features.module.modules.visual.ESP
import net.asd.union.features.module.modules.visual.NameTags
import net.asd.union.features.module.modules.visual.Tracers
import net.asd.union.utils.attack.EntityUtils
import net.asd.union.utils.extensions.isAnimal
import net.asd.union.utils.extensions.isMob
import net.asd.union.utils.rotation.RotationUtils
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer

object EntityCache : Listenable, MinecraftInstance {
    private const val UPDATE_CYCLE = 5

    private val validEntities = mutableListOf<EntityLivingBase>()
    private val playerEntities = mutableListOf<EntityPlayer>()
    private val mobEntities = mutableListOf<EntityLivingBase>()
    private val animalEntities = mutableListOf<EntityLivingBase>()

    private var ticks = 0
    var maxRenderDistanceSq = 10_000.0

    private val cachedFilteredLists = LinkedHashMap<String, List<EntityLivingBase>>()

    override fun handleEvents(): Boolean {
        return ESP.state || NameTags.state || Tracers.state
    }

    private fun updateEntityCache() {
        validEntities.clear()
        playerEntities.clear()
        mobEntities.clear()
        animalEntities.clear()

        for (entity in mc.theWorld?.loadedEntityList.orEmpty()) {
            if (entity !is EntityLivingBase || entity == mc.thePlayer || !isValidEntity(entity)) {
                continue
            }

            validEntities += entity

            when {
                entity is EntityPlayer -> playerEntities += entity
                entity.isMob() -> mobEntities += entity
                entity.isAnimal() -> animalEntities += entity
            }
        }
    }

    private fun isValidEntity(entity: EntityLivingBase): Boolean {
        val player = mc.thePlayer ?: return false

        if (!TargetModule.deadValue && !entity.isEntityAlive) {
            return false
        }

        if (!TargetModule.invisibleValue && entity.isInvisible) {
            return false
        }

        if (player.getDistanceSqToEntity(entity) > maxRenderDistanceSq) {
            return false
        }

        val isPlayer = entity is EntityPlayer
        val isMob = entity.isMob()
        val isAnimal = entity.isAnimal()

        if (!TargetModule.playerValue && isPlayer) {
            return false
        }

        if (!TargetModule.mobValue && isMob) {
            return false
        }

        if (!TargetModule.animalValue && isAnimal) {
            return false
        }

        return true
    }

    fun getAllValidEntities(): List<EntityLivingBase> = validEntities.toList()

    fun getPlayerEntities(): List<EntityPlayer> = playerEntities.toList()

    fun getMobEntities(): List<EntityLivingBase> = mobEntities.toList()

    fun getAnimalEntities(): List<EntityLivingBase> = animalEntities.toList()

    fun getEntitiesInRange(range: Double): List<EntityLivingBase> {
        val rangeSq = range * range
        return validEntities.filter { mc.thePlayer.getDistanceSqToEntity(it) <= rangeSq }
    }

    fun getEntitiesOnLook(maxAngleDifference: Double): List<EntityLivingBase> {
        return validEntities.filter { EntityUtils.isLookingOnEntities(it, maxAngleDifference) }
    }

    fun getEntitiesVisibleThroughBlocks(): List<EntityLivingBase> {
        return validEntities.filter { RotationUtils.isEntityHeightVisible(it) }
    }

    fun getEntitiesWithValidityCheck(
        canAttack: Boolean = false,
        range: Double = maxRenderDistanceSq,
        onLook: Boolean = false,
        maxAngleDifference: Double = 90.0,
        thruBlocks: Boolean = true,
    ): List<EntityLivingBase> {
        val cacheKey =
            "canAttack=$canAttack,range=$range,onLook=$onLook,maxAngle=$maxAngleDifference,thruBlocks=$thruBlocks"

        cachedFilteredLists[cacheKey]?.let { return it }

        var filtered = validEntities.toList()

        if (range < maxRenderDistanceSq) {
            val rangeSq = range * range
            filtered = filtered.filter { mc.thePlayer.getDistanceSqToEntity(it) <= rangeSq }
        }

        if (onLook) {
            filtered = filtered.filter { EntityUtils.isLookingOnEntities(it, maxAngleDifference) }
        }

        if (!thruBlocks) {
            filtered = filtered.filter { RotationUtils.isEntityHeightVisible(it) }
        }

        if (canAttack) {
            filtered = filtered.filter { EntityUtils.isSelected(it, true) }
        }

        cachedFilteredLists[cacheKey] = filtered
        return filtered
    }

    val onUpdate = handler<UpdateEvent> {
        ticks = (ticks + 1) % UPDATE_CYCLE

        if (ticks != 0) {
            return@handler
        }

        updateEntityCache()
        cachedFilteredLists.clear()
    }
}
