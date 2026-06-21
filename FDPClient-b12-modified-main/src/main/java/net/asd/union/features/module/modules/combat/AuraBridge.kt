package net.asd.union.features.module.modules.combat

import net.asd.union.features.module.Module
import net.minecraft.entity.EntityLivingBase

object AuraBridge {

    @JvmStatic
    fun getActiveAuraModule(): Module? {
        val killAuraActive = KillAura.handleEvents()

        return when {
            killAuraActive && KillAura.target != null -> KillAura
            killAuraActive -> KillAura
            else -> null
        }
    }

    @JvmStatic
    fun getActiveTarget(): EntityLivingBase? {
        return when (val module = getActiveAuraModule()) {
            KillAura -> KillAura.target
            else -> null
        }
    }

    @JvmStatic
    fun isAuraActive(): Boolean = getActiveAuraModule() != null

    @JvmStatic
    fun isBlocking(): Boolean = KillAura.blockStatus

    @JvmStatic
    fun isRenderBlocking(): Boolean = KillAura.renderBlocking

    @JvmStatic
    fun shouldForceBlockRender(): Boolean {
        if (KillAura.handleEvents() && KillAura.autoBlock != "Off") {
            if (KillAura.renderBlocking || KillAura.target != null && (KillAura.blinkAutoBlock || KillAura.forceBlockRender)) {
                return true
            }
        }

        return false
    }
}
