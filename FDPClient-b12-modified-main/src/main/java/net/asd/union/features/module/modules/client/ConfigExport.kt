/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.asd.union.config.boolean
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.FDPClient
import net.asd.union.file.FileManager
import net.asd.union.utils.client.chat
import org.lwjgl.input.Keyboard
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ConfigExport module exports all current module states and their values to a JSON file
 * that can be imported using FDP's config import functionality.
 * 
 * This allows users to easily share their configurations or backup their settings.
 * 
 * @author opZywl
 */
object ConfigExport : Module("ConfigExport", Category.CLIENT, Keyboard.KEY_NONE) {

    // Configuration options
    private val includeDisabledModules by boolean("IncludeDisabled", false)
    private val autoDisable by boolean("AutoDisable", true)
    private val showMessages by boolean("ShowMessages", true)

    override fun onEnable() {
        try {
            exportConfig()
        } catch (e: Exception) {
            if (showMessages) {
                chat("§c§lConfigExport §7» §cError: ${e.message}")
            }
        }

        if (autoDisable) {
            state = false
        }
    }

    /**
     * Export all module configurations to a JSON file
     */
    private fun exportConfig() {
        val exportData = JsonObject()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        
        // Add metadata
        val metadata = JsonObject()
        metadata.addProperty("exportTime", timestamp)
        metadata.addProperty("clientVersion", FDPClient.CLIENT_VERSION)
        metadata.addProperty("clientCommit", FDPClient.clientCommit)
        metadata.addProperty("includeDisabled", includeDisabledModules)
        exportData.add("metadata", metadata)

        // Export module states and values
        val modulesData = JsonObject()
        var exportedCount = 0
        var enabledCount = 0

        for (module in FDPClient.moduleManager) {
            // Skip disabled modules if not including them
            if (!includeDisabledModules && !module.state) continue

            val moduleData = JsonObject()
            
            // Add module state
            moduleData.addProperty("enabled", module.state)
            if (module.state) enabledCount++
            
            // Add keybind if set
            if (module.keyBind != Keyboard.KEY_NONE) {
                moduleData.addProperty("keyBind", module.keyBind)
            }

            // Add module values
            val valuesData = JsonObject()
            var valueCount = 0

            for (value in module.values) {
                try {
                    // Export all values since we can't easily check defaults
                    when (value) {
                        is net.asd.union.config.BoolValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.IntegerValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.FloatValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.TextValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.ListValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.BlockValue -> {
                            valuesData.addProperty(value.name, value.get())
                            valueCount++
                        }
                        is net.asd.union.config.ColorValue -> {
                            // Export color as hex string
                            val color = value.get()
                            val hexColor = String.format("#%02X%02X%02X", color.red, color.green, color.blue)
                            valuesData.addProperty(value.name, hexColor)
                            valueCount++
                        }
                        is net.asd.union.config.FontValue -> {
                            // Export font information
                            val fontData = JsonObject()
                            val fontDetails = net.asd.union.ui.font.Fonts.getFontDetails(value.get())
                            if (fontDetails != null) {
                                fontData.addProperty("fontName", fontDetails.name)
                                fontData.addProperty("fontSize", fontDetails.size)
                                valuesData.add(value.name, fontData)
                                valueCount++
                            }
                        }
                        is net.asd.union.config.FloatRangeValue -> {
                            // Export range as object
                            val rangeData = JsonObject()
                            rangeData.addProperty("min", value.get().start)
                            rangeData.addProperty("max", value.get().endInclusive)
                            valuesData.add(value.name, rangeData)
                            valueCount++
                        }
                        is net.asd.union.config.IntegerRangeValue -> {
                            // Export range as object
                            val rangeData = JsonObject()
                            rangeData.addProperty("min", value.get().first)
                            rangeData.addProperty("max", value.get().last)
                            valuesData.add(value.name, rangeData)
                            valueCount++
                        }
                        else -> {
                            // Handle unknown value types by converting to string
                            try {
                                valuesData.addProperty(value.name, value.get().toString())
                                valueCount++
                            } catch (e: Exception) {
                                // Skip if conversion fails
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip problematic values
                    continue
                }
            }

            // Only add module if it has values or is enabled
            if (valueCount > 0 || module.state || includeDisabledModules) {
                if (valueCount > 0) {
                    moduleData.add("values", valuesData)
                }
                modulesData.add(module.name, moduleData)
                exportedCount++
            }
        }

        exportData.add("modules", modulesData)

        // Export additional configs
        exportAdditionalConfigs(exportData)

        // Write to file
        val exportFile = File(FileManager.dir, "config_export_$timestamp.json")
        val gson = GsonBuilder().setPrettyPrinting().create()
        
        try {
            FileManager.writeFile(exportFile, gson.toJson(exportData))
            
            if (showMessages) {
                chat("§a§lConfigExport §7» §aSuccessfully exported config!")
                chat("§a§lConfigExport §7» §7File: §f${exportFile.name}")
                chat("§a§lConfigExport §7» §7Modules: §f$exportedCount §7(§f$enabledCount §7enabled)")
            }
        } catch (e: Exception) {
            if (showMessages) {
                chat("§c§lConfigExport §7» §cFailed to write export file: ${e.message}")
            }
            throw e
        }
    }

    /**
     * Export additional configuration data (friends, accounts, etc.)
     */
    private fun exportAdditionalConfigs(exportData: JsonObject) {
        try {
            // Export friends
            val friendsData = JsonObject()
            val friendsList = mutableListOf<String>()
            for (friend in FileManager.friendsConfig.friends) {
                friendsList.add(friend.playerName)
            }
            friendsData.addProperty("count", friendsList.size)
            friendsData.add("list", FileManager.PRETTY_GSON.toJsonTree(friendsList))
            exportData.add("friends", friendsData)

            // Export command prefix
            val commandData = JsonObject()
            commandData.addProperty("prefix", FDPClient.commandManager.prefix)
            exportData.add("commands", commandData)

            // Export HUD settings (basic info only)
            val hudData = JsonObject()
            hudData.addProperty("hasCustomHUD", FileManager.hudConfig.file.exists())
            exportData.add("hud", hudData)

        } catch (e: Exception) {
            // Continue even if additional configs fail
        }
    }

}