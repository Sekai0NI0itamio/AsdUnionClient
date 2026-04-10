/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.FDPClient.moduleManager
import net.asd.union.features.command.Command
import net.asd.union.ui.client.hud.HUD.addNotification
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import org.lwjgl.input.Keyboard

object BindCommand : Command("bind") {
    /**
     * Execute commands with provided [args]
     */
    override fun execute(args: Array<String>) {
        if (args.size > 2) {
            // Get module by name
            val module = moduleManager[args[1]]

            if (module == null) {
                chat("Module §a§l" + args[1] + "§3 not found.")
                return
            }
            // Find key by name and change
            val key = Keyboard.getKeyIndex(args[2].uppercase())
            module.keyBind = key

            // Response to user
            chat("Bound module §a§l${module.getName()}§3 to key §a§l${Keyboard.getKeyName(key)}§3.")
            addNotification(Notification("Bound ${module.getName()} to ${Keyboard.getKeyName(key)}", "SUCESS", Type.SUCCESS))
            playEdit()
            return
        }

        chatSyntax(arrayOf("<module> <key>", "<module> none"))
    }

    override fun tabComplete(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()

        val moduleName = args[0]

        return when (args.size) {
            1 -> moduleManager
                .map { it.name }
                .filter { it.startsWith(moduleName, true) }
            else -> emptyList()
        }
    }
}