/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.config.boolean
import net.asd.union.event.RotationSetEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.movement.Sneak
import net.asd.union.utils.extensions.prevRotation
import net.asd.union.utils.extensions.rotation
import net.asd.union.utils.rotation.Rotation

object FreeLook : Module("FreeLook", Category.VISUAL) {

    private val autoF5 by boolean("AutoF5", true, subjective = true)
    private val blockPitchWhenSneaking by boolean("BlockPitchWhenSneaking", true, subjective = true)

    // The module's rotations
    private var currRotation = Rotation.ZERO
    private var prevRotation = currRotation

    // The player's rotations
    private var savedCurrRotation = Rotation.ZERO
    private var savedPrevRotation = Rotation.ZERO

    private var modifySavedRotations = true

    private fun isSneakKeyManuallyPressed(): Boolean {
        val sneakBind = mc.gameSettings.keyBindSneak
        return sneakBind.isKeyDown && !Sneak.handleEvents()
    }

    override fun onEnable() {
        mc.thePlayer?.run {
            if (autoF5 && mc.gameSettings.thirdPersonView != 1) {
                mc.gameSettings.thirdPersonView = 1
            }

            currRotation = rotation
            prevRotation = prevRotation
        }
    }

    override fun onDisable() {
        if (autoF5) mc.gameSettings.thirdPersonView = 0
    }

    val onRotationSet = handler<RotationSetEvent> { event ->
        if (mc.gameSettings.thirdPersonView != 0) {
            event.cancelEvent()
        } else {
            currRotation = mc.thePlayer.rotation
            prevRotation = currRotation
        }

        prevRotation = currRotation

        val shouldBlockPitch = blockPitchWhenSneaking && 
            (Sneak.handleEvents() || mc.thePlayer?.isSneaking == true) && 
            !isSneakKeyManuallyPressed()

        if (shouldBlockPitch) {
            currRotation += Rotation(event.yawDiff, 0f)
        } else {
            currRotation += Rotation(event.yawDiff, -event.pitchDiff)
        }

        currRotation.withLimitedPitch()
    }

    fun useModifiedRotation() {
        val player = mc.thePlayer ?: return

        if (mc.gameSettings.thirdPersonView == 0)
            return

        if (modifySavedRotations) {
            savedCurrRotation = player.rotation
            savedPrevRotation = player.prevRotation
        }

        if (!handleEvents())
            return

        player.rotation = currRotation
        player.prevRotation = prevRotation
    }

    fun restoreOriginalRotation() {
        val player = mc.thePlayer ?: return

        if (!handleEvents() || mc.gameSettings.thirdPersonView == 0)
            return

        player.rotation = savedCurrRotation
        player.prevRotation = savedPrevRotation
    }

    fun runWithoutSavingRotations(f: () -> Unit) {
        modifySavedRotations = false

        try {
            f()
        } catch (_: Exception) {
        }

        modifySavedRotations = true
    }
}