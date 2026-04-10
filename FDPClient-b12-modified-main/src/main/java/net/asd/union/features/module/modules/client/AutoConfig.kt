/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.asd.union.config.boolean
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.FDPClient
import net.asd.union.utils.client.chat
import org.lwjgl.input.Keyboard
import java.io.InputStreamReader

/**
 * AutoConfig module - "Itamio's Config"
 * Automatically applies a pre-configured set of module settings from embedded resources
 * 
 * This module loads the configuration from itamio_config.json in resources and applies
 * all the settings to provide an optimized client setup.
 * 
 * @author opZywl
 */
object AutoConfig : Module("AutoConfig", Category.CLIENT, Keyboard.KEY_NONE) {

    // Configuration options
    private val showMessages by boolean("ShowMessages", true)
    private val autoDisable by boolean("AutoDisable", true)
    private val applyKeybinds by boolean("ApplyKeybinds", true)
    private val onlyEnabledModules by boolean("OnlyEnabledModules", false)


    override fun onEnable() {
        try {
            applyItamioConfig()
        } catch (e: Exception) {
            if (showMessages) {
                chat("§c§lItamio's Config §7» §cError: ${e.message}")
            }
        }

        if (autoDisable) {
            state = false
        }
    }

    /**
     * Apply Itamio's pre-configured settings from embedded resources
     */
    private fun applyItamioConfig() {
        if (showMessages) {
            chat("§a§lItamio's Config §7» §7Loading configuration...")
        }

        // Load config from resources
        val configStream = AutoConfig::class.java.classLoader.getResourceAsStream("itamio_config.json")
            ?: throw Exception("Configuration file not found in resources")

        val configData = JsonParser().parse(InputStreamReader(configStream)).asJsonObject
        
        // Validate config structure
        if (!configData.has("modules")) {
            throw Exception("Invalid configuration format - missing modules section")
        }

        val modulesData = configData.getAsJsonObject("modules")
        var appliedCount = 0
        var enabledCount = 0
        var keybindCount = 0

        // Apply configuration to each module
        for (module in FDPClient.moduleManager) {
            val moduleName = module.name
            
            if (!modulesData.has(moduleName)) continue

            val moduleConfig = modulesData.getAsJsonObject(moduleName)
            
            // Skip disabled modules if onlyEnabledModules is true
            val shouldEnable = moduleConfig.get("enabled")?.asBoolean ?: false
            if (onlyEnabledModules && !shouldEnable) continue

            try {
                // Apply module state
                if (moduleConfig.has("enabled")) {
                    val wasEnabled = module.state
                    module.state = shouldEnable
                    if (shouldEnable && !wasEnabled) enabledCount++
                }

                // Apply keybind if present and option is enabled
                if (applyKeybinds && moduleConfig.has("keyBind")) {
                    val keyBind = moduleConfig.get("keyBind").asInt
                    if (keyBind != Keyboard.KEY_NONE) {
                        module.keyBind = keyBind
                        keybindCount++
                    }
                }

                // Apply module values
                if (moduleConfig.has("values")) {
                    val valuesConfig = moduleConfig.getAsJsonObject("values")
                    applyModuleValues(module, valuesConfig)
                }

                appliedCount++

            } catch (e: Exception) {
        if (showMessages) {
            chat("§c§lItamio's Config §7» §cError applying config to $moduleName: ${e.message}")
        }
            }
        }

        // Show completion message
        if (showMessages) {
            chat("§a§lItamio's Config §7» §aSuccessfully applied!")
            chat("§a§lItamio's Config §7» §7Modules configured: §a$appliedCount")
            chat("§a§lItamio's Config §7» §7Modules enabled: §a$enabledCount")
            if (applyKeybinds && keybindCount > 0) {
                chat("§a§lItamio's Config §7» §7Keybinds applied: §a$keybindCount")
            }
        }
    }

    /**
     * Apply values to a specific module
     */
    private fun applyModuleValues(module: Module, valuesConfig: JsonObject) {
        for (value in module.values) {
            if (!valuesConfig.has(value.name)) continue

            try {
                when (value) {
                    is net.asd.union.config.BoolValue -> {
                        value.set(valuesConfig.get(value.name).asBoolean)
                    }
                    is net.asd.union.config.IntegerValue -> {
                        value.set(valuesConfig.get(value.name).asInt)
                    }
                    is net.asd.union.config.FloatValue -> {
                        value.set(valuesConfig.get(value.name).asFloat)
                    }
                    is net.asd.union.config.TextValue -> {
                        value.set(valuesConfig.get(value.name).asString)
                    }
                    is net.asd.union.config.ListValue -> {
                        val stringValue = valuesConfig.get(value.name).asString
                        if (value.values.contains(stringValue)) {
                            value.set(stringValue)
                        }
                    }
                    is net.asd.union.config.BlockValue -> {
                        value.set(valuesConfig.get(value.name).asInt)
                    }
                    is net.asd.union.config.ColorValue -> {
                        val colorString = valuesConfig.get(value.name).asString
                        if (colorString.startsWith("#")) {
                            try {
                                val color = java.awt.Color.decode(colorString)
                                value.set(color)
                            } catch (e: NumberFormatException) {
                                // Skip invalid color values
                            }
                        }
                    }
                    is net.asd.union.config.FontValue -> {
                        val fontConfig = valuesConfig.getAsJsonObject(value.name)
                        if (fontConfig != null && fontConfig.has("fontName") && fontConfig.has("fontSize")) {
                            val fontName = fontConfig.get("fontName").asString
                            val fontSize = fontConfig.get("fontSize").asInt
                            val fontRenderer = net.asd.union.ui.font.Fonts.getFontRenderer(fontName, fontSize)
                            if (fontRenderer != null) {
                                value.set(fontRenderer)
                            }
                        }
                    }
                    is net.asd.union.config.IntegerRangeValue -> {
                        val rangeConfig = valuesConfig.getAsJsonObject(value.name)
                        if (rangeConfig != null && rangeConfig.has("min") && rangeConfig.has("max")) {
                            val min = rangeConfig.get("min").asInt
                            val max = rangeConfig.get("max").asInt
                            value.set(min..max)
                        }
                    }
                    is net.asd.union.config.FloatRangeValue -> {
                        val rangeConfig = valuesConfig.getAsJsonObject(value.name)
                        if (rangeConfig != null && rangeConfig.has("min") && rangeConfig.has("max")) {
                            val min = rangeConfig.get("min").asFloat
                            val max = rangeConfig.get("max").asFloat
                            value.set(min..max)
                        }
                    }
                    else -> {
                        // Handle any other value types that might be added in the future
                    }
                }
            } catch (e: Exception) {
                // Skip invalid values silently
            }
        }
    }
}