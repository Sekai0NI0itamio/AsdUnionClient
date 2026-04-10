/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.event.JumpEvent
import net.asd.union.event.MoveEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.movement.longjumpmodes.aac.AACv1
import net.asd.union.features.module.modules.movement.longjumpmodes.aac.AACv2
import net.asd.union.features.module.modules.movement.longjumpmodes.aac.AACv3
import net.asd.union.features.module.modules.movement.longjumpmodes.ncp.NCP
import net.asd.union.features.module.modules.movement.longjumpmodes.other.Buzz
import net.asd.union.features.module.modules.movement.longjumpmodes.other.Hycraft
import net.asd.union.features.module.modules.movement.longjumpmodes.other.Redesky
import net.asd.union.features.module.modules.movement.longjumpmodes.other.VerusDamage
import net.asd.union.features.module.modules.movement.longjumpmodes.other.VerusDamage.damaged
import net.asd.union.utils.extensions.isMoving
import net.asd.union.utils.extensions.tryJump

object LongJump : Module("LongJump", Category.MOVEMENT) {

    private val longJumpModes = arrayOf(
        // NCP
        NCP,

        // AAC
        AACv1, AACv2, AACv3,

        // Other
        Redesky, Hycraft, Buzz, VerusDamage
    )

    private val modes = longJumpModes.map { it.modeName }.toTypedArray()

    val mode by choices("Mode", modes, "NCP")
    val ncpBoost by float("NCPBoost", 4.25f, 1f..10f) { mode == "NCP" }

    private val autoJump by boolean("AutoJump", true)

    val autoDisable by boolean("AutoDisable", true) { mode == "VerusDamage" }

    var jumped = false
    var canBoost = false
    var teleported = false

    val onUpdate = handler<UpdateEvent> {
        if (jumped) {
            val mode = mode

            if (mc.thePlayer.onGround || mc.thePlayer.capabilities.isFlying) {
                jumped = false

                if (mode == "NCP") {
                    mc.thePlayer.motionX = 0.0
                    mc.thePlayer.motionZ = 0.0
                }
                return@handler
            }

            modeModule.onUpdate()
        }
        if (autoJump && mc.thePlayer.onGround && mc.thePlayer.isMoving) {
            if (autoDisable && !damaged) {
                return@handler
            }

            jumped = true
            mc.thePlayer.tryJump()
        }
    }

    val onMove = handler<MoveEvent> { event ->
        modeModule.onMove(event)
    }

    override fun onEnable() {
        modeModule.onEnable()
    }

    override fun onDisable() {
        modeModule.onDisable()
    }

    val onJump = handler<JumpEvent>(always = true) { event ->
        jumped = true
        canBoost = true
        teleported = false

        if (handleEvents()) {
            modeModule.onJump(event)
        }
    }

    override val tag
        get() = mode

    private val modeModule
        get() = longJumpModes.find { it.modeName == mode }!!
}
