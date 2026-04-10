/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.features.command.Command
import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.macro.Macro
import net.asd.union.handler.macro.MacroManager
import net.asd.union.utils.kotlin.StringUtils
import org.lwjgl.input.Keyboard

object MacroCommand : Command("macro", "m") {
    override fun execute(args: Array<String>) {
        if (args.size > 1) {
            val arg1 = args[1]
            when (arg1.lowercase()) {
                "add" -> {
                    if (args.size > 3) {
                        val key = Keyboard.getKeyIndex(args[2].uppercase())
                        if (key != Keyboard.KEY_NONE) {
                            var comm = StringUtils.toCompleteString(args, 3)
                            if (!comm.startsWith(".")) comm = ".$comm"
                            MacroManager.macros.add(Macro(key, comm))
                            alert("Bound macro $comm to key ${Keyboard.getKeyName(key)}.")
                        } else {
                            alert("Unknown key to bind macro.")
                        }
                        save()
                    } else {
                        chatSyntax("macro add <key> <macro>")
                    }
                }

                "remove" -> {
                    if (args.size > 2) {
                        if (args[2].startsWith(".")) {
                            MacroManager.macros.filter { it.command == StringUtils.toCompleteString(args, 2) }
                        } else {
                            val key = Keyboard.getKeyIndex(args[2].uppercase())
                            MacroManager.macros.filter { it.key == key }
                        }.forEach {
                            MacroManager.macros.remove(it)
                            alert("Remove macro ${it.command}.")
                        }
                        save()
                    } else {
                        chatSyntax("macro remove <macro/key>")
                    }
                }

                "list" -> {
                    alert("Macros:")
                    MacroManager.macros.forEach {
                        alert("key=${Keyboard.getKeyName(it.key)}, command=${it.command}")
                    }
                }

                else -> chatSyntax("macro <add/remove/list>")
            }
            return
        }
        chatSyntax("macro <add/remove/list>")
    }

    private fun save() {
        saveConfig(valuesConfig)
        playEdit()
    }
}