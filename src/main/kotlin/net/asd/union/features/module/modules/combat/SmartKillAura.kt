package net.asd.union.features.module.modules.combat

import net.asd.union.config.IntegerValue
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import org.lwjgl.input.Keyboard

/**
 * SmartKillAura is a small wrapper around [KillAura] that forces its hurt-time gating to be strict.
 *
 * Vanilla (1.8.x) damage immunity ("i-frames") prevents hits from dealing damage for a short period after being hit.
 * By setting KillAura's `HurtTime` to `0`, KillAura will only attack when the target can be damaged again.
 */
object SmartKillAura : Module("SmartKillAura", Category.COMBAT, Keyboard.KEY_NONE, hideModule = false) {

    private var previousKillAuraHurtTime: Int? = null
    private var killAuraWasEnabled = false
    private var cachedKillAuraHurtTimeValue: IntegerValue? = null

    private fun killAuraHurtTimeValue(): IntegerValue? {
        cachedKillAuraHurtTimeValue?.let { return it }
        val resolved = KillAura.getValue("HurtTime") as? IntegerValue
        cachedKillAuraHurtTimeValue = resolved
        return resolved
    }

    private fun enforceStrictHurtTime() {
        killAuraHurtTimeValue()?.let { hurtTime ->
            if (hurtTime.get() != 0) {
                hurtTime.set(0)
            }
        }
    }

    private val onUpdate = handler<UpdateEvent> {
        // If the user disables KillAura while SmartKillAura is active, disable SmartKillAura too.
        if (!KillAura.state) {
            state = false
            return@handler
        }

        enforceStrictHurtTime()
    }

    override fun onEnable() {
        cachedKillAuraHurtTimeValue = null
        previousKillAuraHurtTime = killAuraHurtTimeValue()?.get()
        killAuraWasEnabled = KillAura.state

        if (!KillAura.state) {
            KillAura.state = true
        }

        enforceStrictHurtTime()
    }

    override fun onDisable() {
        previousKillAuraHurtTime?.let { original ->
            killAuraHurtTimeValue()?.set(original)
        }

        previousKillAuraHurtTime = null
        cachedKillAuraHurtTimeValue = null

        if (!killAuraWasEnabled && KillAura.state) {
            KillAura.state = false
        }

        killAuraWasEnabled = false
    }
}

