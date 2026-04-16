package net.asd.union.features.module.modules.other

import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.handler.sessiontabs.ClientTabManager
import org.lwjgl.input.Keyboard

object LinkBots : Module("LinkBots", Category.OTHER, Keyboard.KEY_NONE, gameDetecting = false, hideModule = false) {

    fun isLinkedControlActive(): Boolean = state && ClientTabManager.isMainTabActive()

    override fun onDisable() {
        ClientTabManager.clearLinkedBotRuntimeState()
    }
}
