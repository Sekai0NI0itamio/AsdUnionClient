/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.config.boolean
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.file.FileManager
import net.asd.union.utils.client.chat
import java.io.File

object DefaultConfig : Module("DefaultConfig", Category.CLIENT) {

    private val showMessages by boolean("ShowMessages", true)
    private val autoDisable by boolean("AutoDisable", true)

    private val configFiles = arrayOf(
        "modules.json",
        "values.json",
        "clickgui.json",
        "hud.json",
        "colorTheme.json",
    )

    override fun onEnable() {
        try {
            applyDefaultConfig()
        } catch (exception: Exception) {
            if (showMessages) {
                chat("§c§lDefaultConfig §7» §cError: ${exception.message}")
            }
        }

        if (autoDisable) {
            state = false
        }
    }

    private fun applyDefaultConfig() {
        if (showMessages) {
            chat("§a§lDefaultConfig §7» §7Applying default configuration...")
        }

        val copiedFiles = mutableListOf<String>()

        for (configName in configFiles) {
            try {
                val resourcePath = "/default_config/$configName"
                val inputStream = DefaultConfig::class.java.classLoader
                    .getResourceAsStream(resourcePath.removePrefix("/"))
                    ?: javaClass.getResourceAsStream(resourcePath)

                if (inputStream == null) {
                    if (showMessages) {
                        chat("§e§lDefaultConfig §7» §eSkipped $configName (not found in resources)")
                    }
                    continue
                }

                val targetFile = File(FileManager.dir, configName)

                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                copiedFiles += configName
            } catch (exception: Exception) {
                if (showMessages) {
                    chat("§c§lDefaultConfig §7» §cFailed to copy $configName: ${exception.message}")
                }
            }
        }

        if (copiedFiles.isEmpty()) {
            if (showMessages) {
                chat("§c§lDefaultConfig §7» §cNo config files were applied.")
            }
            return
        }

        try {
            FileManager.loadAllConfigs()
        } catch (exception: Exception) {
            if (showMessages) {
                chat("§e§lDefaultConfig §7» §eConfigs copied but reload had an error: ${exception.message}")
            }
        }

        if (showMessages) {
            chat("§a§lDefaultConfig §7» §aApplied ${copiedFiles.size} config files successfully!")
            chat("§a§lDefaultConfig §7» §7Files: ${copiedFiles.joinToString(", ")}")
        }
    }
}
