/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.command.commands

import net.asd.union.FDPClient.isStarting
import net.asd.union.FDPClient.moduleManager
import net.asd.union.features.command.Command
import net.asd.union.features.command.CommandManager
import net.asd.union.file.FileManager.accountsConfig
import net.asd.union.file.FileManager.clickGuiConfig
import net.asd.union.file.FileManager.friendsConfig
import net.asd.union.file.FileManager.hudConfig
import net.asd.union.file.FileManager.loadConfig
import net.asd.union.file.FileManager.modulesConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.script.ScriptManager.disableScripts
import net.asd.union.script.ScriptManager.reloadScripts
import net.asd.union.script.ScriptManager.unloadScripts
import net.asd.union.ui.font.Fonts

object ReloadCommand : Command("reload", "configreload") {
    /**
     * Execute commands with provided [args]
     */
    override fun execute(args: Array<String>) {
        chat("Reloading...")
        isStarting = true

        chat("§c§lReloading commands...")
        CommandManager.registerCommands()

        disableScripts()
        unloadScripts()

        for (module in moduleManager)
            moduleManager.generateCommand(module)

        chat("§c§lReloading scripts...")
        reloadScripts()

        chat("§c§lReloading fonts...")
        Fonts.loadFonts()

        chat("§c§lReloading modules...")
        loadConfig(modulesConfig)


        chat("§c§lReloading values...")
        loadConfig(valuesConfig)

        chat("§c§lReloading accounts...")
        loadConfig(accountsConfig)

        chat("§c§lReloading friends...")
        loadConfig(friendsConfig)

        chat("§c§lReloading HUD...")
        loadConfig(hudConfig)

        chat("§c§lReloading ClickGUI...")
        loadConfig(clickGuiConfig)

        isStarting = false
        chat("Reloaded.")
    }
}
