/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.FDPClient.commandManager
import net.asd.union.features.command.Command
import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig

object PrefixCommand : Command("prefix") {
    /**
     * Execute commands with provided [args]
     */
    override fun execute(args: Array<String>) {
        if (args.size <= 1) {
            chatSyntax("prefix <character>")
            return
        }

        val prefix = args[1]

        if (prefix.length > 1) {
            chat("§cPrefix can only be one character long!")
            return
        }

        commandManager.prefix = prefix.single()
        saveConfig(valuesConfig)

        chat("Successfully changed command prefix to '§8$prefix§3'")
    }
}