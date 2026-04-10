/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.features.command.Command
import net.asd.union.features.module.modules.other.AutoText
import net.asd.union.utils.kotlin.StringUtils

object AutoTextCommand : Command("autotext", "at") {
    /**
     * Execute commands with provided [args]
     */
    override fun execute(args: Array<String>) {
        val usedAlias = args[0].lowercase()

        if (args.size <= 1) {
            chatSyntax("$usedAlias <add/remove/list/clear>")
            return
        }

        when (args[1].lowercase()) {
            "add" -> {
                if (args.size <= 2) {
                    chatSyntax("$usedAlias add <message>")
                    return
                }

                val message = StringUtils.toCompleteString(args, 2)

                if (AutoText.addMessage(message)) {
                    chat("§a§lMessage added §7(ID: ${AutoText.getMessageCount()})")
                    chat("§7> §f$message")
                    playEdit()
                } else {
                    chat("§cFailed to add message. Message cannot be empty.")
                }
            }

            "remove" -> {
                if (args.size <= 2) {
                    chatSyntax("$usedAlias remove <id>")
                    return
                }

                val id = args[2].toIntOrNull()
                if (id == null) {
                    chat("§cInvalid ID. Please enter a number.")
                    return
                }

                if (AutoText.removeMessage(id)) {
                    chat("§a§lMessage #$id removed.")
                    playEdit()
                } else {
                    chat("§cMessage with ID $id not found.")
                }
            }

            "clear" -> {
                val count = AutoText.getMessageCount()
                AutoText.clearMessages()
                chat("§a§lCleared $count message(s).")
                playEdit()
            }

            "list" -> {
                val messages = AutoText.getMessages()
                if (messages.isEmpty()) {
                    chat("§cNo messages in list.")
                    return
                }

                chat("§a§lAutoText Messages:")
                for ((id, message) in messages) {
                    chat("§7[$id] §f$message")
                }
                chat("§7Total: §f${messages.size} message(s)")
            }

            else -> {
                chatSyntax("$usedAlias <add/remove/list/clear>")
            }
        }
    }

    override fun tabComplete(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()

        return when (args.size) {
            1 -> listOf("add", "remove", "list", "clear").filter { it.startsWith(args[0], true) }
            2 -> {
                when (args[0].lowercase()) {
                    "remove" -> {
                        return AutoText.getMessages()
                            .map { it.first.toString() }
                            .filter { it.startsWith(args[1], true) }
                    }
                }
                return emptyList()
            }
            else -> emptyList()
        }
    }
}
