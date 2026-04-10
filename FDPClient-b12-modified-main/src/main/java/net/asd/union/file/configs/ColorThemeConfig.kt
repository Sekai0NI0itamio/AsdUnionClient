/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.file.configs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.asd.union.file.FileConfig
import net.asd.union.file.FileManager
import net.asd.union.utils.client.ClientThemesUtils
import java.io.File

class ColorThemeConfig(file: File) : FileConfig(file) {

    override fun loadConfig() {
        if (!file.exists()) {
            println("Config file does not exist. Loading default values.")
            loadDefault()
            return
        }

        try {
            val content = file.readText(Charsets.UTF_8)
            val json = JsonParser().parse(content).asJsonObject

            if (json.has("Theme")) {
                ClientThemesUtils.ClientColorMode = json["Theme"].asString
            }
            if (json.has("Fade-Speed")) {
                ClientThemesUtils.ThemeFadeSpeed = json["Fade-Speed"].asInt
            }
            if (json.has("Fade-Type")) {
                ClientThemesUtils.updown = json["Fade-Type"].asBoolean
            }
        } catch (e: Exception) {
            println("Error loading Color Theme Client: ${e.message}")
        }
    }

    override fun saveConfig() {
        try {
            val json = JsonObject()
            json.addProperty("Theme", ClientThemesUtils.ClientColorMode)
            json.addProperty("Fade-Speed", ClientThemesUtils.ThemeFadeSpeed)
            json.addProperty("Fade-Type", ClientThemesUtils.updown)

            file.writeText(FileManager.PRETTY_GSON.toJson(json), Charsets.UTF_8)
        } catch (e: Exception) {
            println("Error saving Color Theme Config: ${e.message}")
        }
    }
}