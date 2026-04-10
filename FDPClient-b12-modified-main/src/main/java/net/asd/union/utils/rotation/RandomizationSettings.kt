/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.rotation

import net.asd.union.features.module.Module
import net.asd.union.config.boolean
import net.asd.union.config.floatRange

class RandomizationSettings(owner: Module, generalApply: () -> Boolean = { true }) {

    val randomize by boolean("RandomizeRotations", false) { generalApply() }
    val yawRandomizationChance by floatRange("YawRandomizationChance", 0.8f..1.0f, 0f..1f) { randomize }
    val yawRandomizationRange by floatRange("YawRandomizationRange", 5f..10f, 0f..30f)
    { randomize && yawRandomizationChance.start != 1F }
    val pitchRandomizationChance by floatRange("PitchRandomizationChance", 0.8f..1.0f, 0f..1f) { randomize }
    val pitchRandomizationRange by floatRange("PitchRandomizationRange", 5f..10f, 0f..30f)
    { randomize && pitchRandomizationChance.start != 1F }

    init {
        owner.addConfigurable(this)
    }
}