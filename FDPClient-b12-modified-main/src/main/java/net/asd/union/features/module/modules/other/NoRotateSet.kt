/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationSettings
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.extensions.rotation
import net.asd.union.utils.timing.WaitTickUtils
import net.asd.union.config.boolean
import net.asd.union.config.intRange
import net.minecraft.entity.player.EntityPlayer

object NoRotateSet : Module("NoRotateSet", Category.OTHER, gameDetecting = false, hideModule = false) {
    var savedRotation = Rotation.ZERO

    private val ignoreOnSpawn by boolean("IgnoreOnSpawn", false)
    val affectRotation by boolean("AffectRotation", true)

    private val ticksUntilStart = intRange("TicksUntilStart", 0..0, 0..20) { affectRotation }
    private val options = RotationSettings(this) { affectRotation }.apply {
        rotationsValue.excludeWithState(true)
        applyServerSideValue.excludeWithState(true)
        resetTicksValue.excludeWithState(1)

        withoutKeepRotation()
    }

    fun shouldModify(player: EntityPlayer) = handleEvents() && (!ignoreOnSpawn || player.ticksExisted != 0)

    fun rotateBackToPlayerRotation() {
        val player = mc.thePlayer ?: return

        currentRotation = player.rotation

        // This connects with the SimulateShortStop code, [performAngleChange] function.
        WaitTickUtils.schedule(ticksUntilStart.random, this)

        setTargetRotation(savedRotation, options = options)
    }
}