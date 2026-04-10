/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.macro

import net.asd.union.FDPClient.commandManager

class Macro(val key: Int, val command: String) {
    fun exec() {
        commandManager.executeCommands(command)
    }
}