/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.client

import net.asd.union.event.Listenable
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.modules.visual.ItemESP
import net.asd.union.utils.attack.EntityUtils
import net.asd.union.utils.rotation.RotationUtils
import net.minecraft.entity.item.EntityItem

object ItemCache : Listenable, MinecraftInstance {
    private const val UPDATE_CYCLE = 5

    private val validItems = mutableListOf<EntityItem>()

    private var ticks = 0
    var maxRenderDistanceSq = 2_500.0
        set(value) {
            if (field == value) {
                return
            }

            field = value
            cachedFilteredLists.clear()
        }

    private val cachedFilteredLists = LinkedHashMap<String, List<EntityItem>>()

    override fun handleEvents(): Boolean = ItemESP.state

    private fun updateItemCache() {
        validItems.clear()
        cachedFilteredLists.clear()

        val player = mc.thePlayer ?: return

        for (entity in mc.theWorld?.loadedEntityList.orEmpty()) {
            if (entity is EntityItem && player.getDistanceSqToEntity(entity) <= maxRenderDistanceSq) {
                validItems += entity
            }
        }
    }

    fun getAllValidItems(): List<EntityItem> = validItems.toList()

    fun getItemsInRange(range: Double): List<EntityItem> {
        val rangeSq = range * range
        return validItems.filter { mc.thePlayer.getDistanceSqToEntity(it) <= rangeSq }
    }

    fun getItemsOnLook(maxAngleDifference: Double): List<EntityItem> {
        return validItems.filter { EntityUtils.isLookingOnEntities(it, maxAngleDifference) }
    }

    fun getItemsVisibleThroughBlocks(): List<EntityItem> {
        return validItems.filter { RotationUtils.isEntityHeightVisible(it) }
    }

    fun getItemsWithValidityCheck(
        range: Double = maxRenderDistanceSq,
        onLook: Boolean = false,
        maxAngleDifference: Double = 90.0,
        thruBlocks: Boolean = true,
    ): List<EntityItem> {
        val cacheKey = "range=$range,onLook=$onLook,maxAngle=$maxAngleDifference,thruBlocks=$thruBlocks"

        cachedFilteredLists[cacheKey]?.let { return it }

        var filtered = validItems.toList()

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

        cachedFilteredLists[cacheKey] = filtered
        return filtered
    }

    val onUpdate = handler<UpdateEvent> {
        ticks = (ticks + 1) % UPDATE_CYCLE

        if (ticks != 0) {
            return@handler
        }

        updateItemCache()
    }
}
