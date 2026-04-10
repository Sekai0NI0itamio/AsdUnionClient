/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.FDPClient.isStarting
import net.asd.union.FDPClient.moduleManager
import net.asd.union.FDPClient.scriptManager
import net.asd.union.features.command.Command
import net.asd.union.features.command.CommandManager
import net.asd.union.file.FileManager.clickGuiConfig
import net.asd.union.file.FileManager.hudConfig
import net.asd.union.file.FileManager.loadConfig
import net.asd.union.file.FileManager.loadConfigs
import net.asd.union.file.FileManager.modulesConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.script.ScriptManager
import net.asd.union.script.ScriptManager.reloadScripts
import net.asd.union.script.ScriptManager.scriptsFolder
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.io.FileFilters
import net.asd.union.utils.io.MiscUtils
import net.asd.union.utils.io.extractZipTo
import java.awt.Desktop

object ScriptManagerCommand : Command("scriptmanager", "scripts") {
    /**
     * Execute commands with provided [args]
     */
    override fun execute(args: Array<String>) {
        val usedAlias = args[0].lowercase()

        if (args.size < 2) {
            chatSyntax("$usedAlias <import/delete/reload/folder>")
            return
        }

        when (args[1].lowercase()) {
            "import" -> {
                try {
                    val file = MiscUtils.openFileChooser(FileFilters.JAVASCRIPT, FileFilters.ARCHIVE) ?: return

                    when (file.extension.lowercase()) {
                        "js" -> {
                            scriptManager.importScript(file)

                            loadConfig(clickGuiConfig)

                            chat("Successfully imported script.")
                        }

                        "zip" -> {
                            val existingFiles = ScriptManager.availableScriptFiles.toSet()

                            file.extractZipTo(scriptsFolder)

                            ScriptManager.availableScriptFiles.filterNot {
                                it in existingFiles
                            }.forEach(scriptManager::loadScript)

                            loadConfigs(clickGuiConfig, hudConfig)

                            chat("Successfully imported script.")
                        }

                        else -> chat("The file extension has to be .js or .zip")
                    }
                } catch (t: Throwable) {
                    LOGGER.error("Something went wrong while importing a script.", t)
                    chat("${t.javaClass.name}: ${t.message}")
                }
            }

            "delete" -> {
                try {
                    if (args.size <= 2) {
                        chatSyntax("$usedAlias delete <index>")
                        return
                    }

                    val scriptIndex = args[2].toInt()

                    if (scriptIndex >= ScriptManager.size) {
                        chat("Index $scriptIndex is too high.")
                        return
                    }

                    val script = ScriptManager[scriptIndex]

                    scriptManager.deleteScript(script)

                    loadConfigs(clickGuiConfig, hudConfig)

                    chat("Successfully deleted script.")
                } catch (numberFormat: NumberFormatException) {
                    chatSyntaxError()
                } catch (t: Throwable) {
                    LOGGER.error("Something went wrong while deleting a script.", t)
                    chat("${t.javaClass.name}: ${t.message}")
                }
            }

            "reload" -> {
                try {
                    CommandManager.registerCommands()

                    isStarting = true

                    reloadScripts()

                    for (module in moduleManager) moduleManager.generateCommand(module)
                    loadConfig(modulesConfig)

                    isStarting = false
                    loadConfigs(valuesConfig, clickGuiConfig, hudConfig)

                    chat("Successfully reloaded all scripts.")
                } catch (t: Throwable) {
                    LOGGER.error("Something went wrong while reloading all scripts.", t)
                    chat("${t.javaClass.name}: ${t.message}")
                }
            }

            "folder" -> {
                try {
                    Desktop.getDesktop().open(scriptsFolder)
                    chat("Successfully opened scripts folder.")
                } catch (t: Throwable) {
                    LOGGER.error("Something went wrong while trying to open your scripts folder.", t)
                    chat("${t.javaClass.name}: ${t.message}")
                }
            }
        }

        return

        val scriptManager = scriptManager

        if (scriptManager.isNotEmpty()) {
            chat("§c§lScripts")
            scriptManager.forEachIndexed { index, script ->
                chat(
                    "$index: §a§l${script.scriptName} §a§lv${script.scriptVersion} §3by §a§l${
                        script.scriptAuthors.joinToString(
                            ", "
                        )
                    }"
                )
            }
        }

        chatSyntax("$usedAlias <import/delete/reload/folder>")
    }

    override fun tabComplete(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()

        return when (args.size) {
            1 -> listOf("delete", "import", "folder", "reload")
                .filter { it.startsWith(args[0], true) }

            else -> emptyList()
        }
    }
}