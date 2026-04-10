/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.event.UpdateEvent
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.config.boolean
import net.asd.union.event.handler
import net.minecraft.potion.Potion

object RemoveEffect : Module("RemoveEffect", Category.OTHER, hideModule = false) {

    private val shouldRemoveSlowness by boolean("Slowness", false)
    private val shouldRemoveMiningFatigue by boolean("Mining Fatigue", false)
    private val shouldRemoveBlindness by boolean("Blindness", false)
    private val shouldRemoveWeakness by boolean("Weakness", false)
    private val shouldRemoveWither by boolean("Wither", false)
    private val shouldRemovePoison by boolean("Poison", false)
    private val shouldRemoveWaterBreathing by boolean("Water Breathing", false)

    override fun onEnable() {}

    val onUpdate = handler<UpdateEvent> (always = true) {

        if (mc.thePlayer != null) {

            val effectIdsToRemove = mutableListOf<Int>()
            if (shouldRemoveSlowness) mc.thePlayer.removePotionEffectClient(Potion.moveSlowdown.id)
            if (shouldRemoveMiningFatigue) mc.thePlayer.removePotionEffectClient(Potion.digSlowdown.id)
            if (shouldRemoveBlindness) mc.thePlayer.removePotionEffectClient(Potion.blindness.id)
            if (shouldRemoveWeakness) mc.thePlayer.removePotionEffectClient(Potion.weakness.id)
            if (shouldRemoveWither) effectIdsToRemove.add(Potion.wither.id)
            if (shouldRemovePoison) effectIdsToRemove.add(Potion.poison.id)
            if (shouldRemoveWaterBreathing) effectIdsToRemove.add(Potion.waterBreathing.id)

            for (effectId in effectIdsToRemove) {
                mc.thePlayer.removePotionEffectClient(effectId)
            }
        }
    }
}
