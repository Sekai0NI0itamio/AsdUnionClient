/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement

import net.asd.union.config.choices
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.features.module.modules.movement.nowebmodes.aac.AAC
import net.asd.union.features.module.modules.movement.nowebmodes.aac.LAAC
import net.asd.union.features.module.modules.movement.nowebmodes.grim.OldGrim
import net.asd.union.features.module.modules.movement.nowebmodes.intave.IntaveNew
import net.asd.union.features.module.modules.movement.nowebmodes.intave.IntaveOld
import net.asd.union.features.module.modules.movement.nowebmodes.other.None
import net.asd.union.features.module.modules.movement.nowebmodes.other.Rewi

object NoWeb : Module("NoWeb", Category.MOVEMENT, hideModule = false) {

    private val noWebModes = arrayOf(
        // Vanilla
        None,

        // AAC
        AAC, LAAC,

        // Intave
        IntaveOld,
        IntaveNew,

        // Grim
        OldGrim,

        // Other
        Rewi,
    )

    private val modes = noWebModes.map { it.modeName }.toTypedArray()

    val mode by choices(
        "Mode", modes, "None"
    )

    val onUpdate = handler<UpdateEvent> {
        modeModule.onUpdate()
    }

    override val tag
        get() = mode

    private val modeModule
        get() = noWebModes.find { it.modeName == mode }!!
}
