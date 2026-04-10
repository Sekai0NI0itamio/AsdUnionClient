/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.macro

import net.asd.union.event.KeyEvent
import net.asd.union.event.Listenable
import net.asd.union.event.handler
import net.asd.union.utils.client.MinecraftInstance

object MacroManager : MinecraftInstance, Listenable {
    val macros = ArrayList<Macro>()

    val onKey = handler<KeyEvent> { event ->
        macros.filter { it.key == event.key }.forEach { it.exec() }
    }

    override fun handleEvents() = true
}