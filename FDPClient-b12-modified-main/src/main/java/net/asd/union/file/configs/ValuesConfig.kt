/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.file.configs

import com.google.gson.JsonObject
import net.asd.union.FDPClient.commandManager
import net.asd.union.FDPClient.moduleManager
import net.asd.union.features.module.modules.client.BrandSpoofer.possibleBrands
import net.asd.union.features.module.modules.client.TargetModule.animalValue
import net.asd.union.features.module.modules.client.TargetModule.deadValue
import net.asd.union.features.module.modules.client.TargetModule.invisibleValue
import net.asd.union.features.module.modules.client.TargetModule.mobValue
import net.asd.union.features.module.modules.client.TargetModule.playerValue
import net.asd.union.utils.io.readJson
import net.asd.union.handler.other.AutoReconnect.delay
import net.asd.union.handler.payload.ClientFixes.blockFML
import net.asd.union.handler.payload.ClientFixes.blockPayloadPackets
import net.asd.union.handler.payload.ClientFixes.blockProxyPacket
import net.asd.union.handler.payload.ClientFixes.blockResourcePackExploit
import net.asd.union.handler.payload.ClientFixes.fmlFixesEnabled
import net.asd.union.file.FileConfig
import net.asd.union.file.FileManager.PRETTY_GSON
import net.asd.union.handler.lang.LanguageManager.overrideLanguage
import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.handler.other.SessionStorage
import net.asd.union.handler.render.AntiSpawnLag
import net.asd.union.handler.render.AntiTranslucent
import net.asd.union.handler.render.LazyChunkCache
import net.asd.union.handler.render.NoChatEffects
import net.asd.union.handler.render.NoTitle
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.altsLength
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.altsPrefix
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.enabledClientTitle
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.stylisedAlts
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.unformattedAlts
import java.io.*

class ValuesConfig(file: File) : FileConfig(file) {

    /**
     * Load config from file
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun loadConfig() {
        val json = file.readJson() as? JsonObject ?: return

        for ((key, value) in json.entrySet()) {
            when {
                key.equals("commandprefix", true) ->
                    commandManager.prefix = value.asCharacter
                key.equals("targets", true) -> {
                    val jsonValue = value as JsonObject
                    if (jsonValue.has("TargetPlayer")) playerValue = jsonValue["TargetPlayer"].asBoolean
                    if (jsonValue.has("TargetMobs")) mobValue = jsonValue["TargetMobs"].asBoolean
                    if (jsonValue.has("TargetAnimals")) animalValue = jsonValue["TargetAnimals"].asBoolean
                    if (jsonValue.has("TargetInvisible")) invisibleValue = jsonValue["TargetInvisible"].asBoolean
                    if (jsonValue.has("TargetDead")) deadValue = jsonValue["TargetDead"].asBoolean
                }
                key.equals("features", true) -> {
                    val jsonValue = value as JsonObject
                    if (jsonValue.has("AntiForge")) fmlFixesEnabled = jsonValue["AntiForge"].asBoolean
                    if (jsonValue.has("AntiForgeFML")) blockFML = jsonValue["AntiForgeFML"].asBoolean
                    if (jsonValue.has("AntiForgeProxy")) blockProxyPacket = jsonValue["AntiForgeProxy"].asBoolean
                    if (jsonValue.has("AntiForgePayloads")) blockPayloadPackets =
                        jsonValue["AntiForgePayloads"].asBoolean
                    if (jsonValue.has("FixResourcePackExploit")) blockResourcePackExploit =
                        jsonValue["FixResourcePackExploit"].asBoolean
                    if (jsonValue.has("ConnectToRouter")) ConnectToRouter.loadEnabledState(jsonValue["ConnectToRouter"].asBoolean)
                    if (jsonValue.has("RouterPhonePassword")) ConnectToRouter.setPhonePassword(
                        jsonValue["RouterPhonePassword"].asString,
                        persist = false,
                    )
                    if (jsonValue.has("AntiSpawnLag")) AntiSpawnLag.enabled = jsonValue["AntiSpawnLag"].asBoolean
                    if (jsonValue.has("LazyChunks")) LazyChunkCache.enabled = jsonValue["LazyChunks"].asBoolean
                    if (jsonValue.has("AntiTranslucent")) AntiTranslucent.enabled =
                        jsonValue["AntiTranslucent"].asBoolean
                    if (jsonValue.has("NoChatEffects")) NoChatEffects.enabled = jsonValue["NoChatEffects"].asBoolean
                    if (jsonValue.has("NoTitle")) NoTitle.enabled = jsonValue["NoTitle"].asBoolean
                    if (jsonValue.has("ClientBrand")) possibleBrands.set(jsonValue["ClientBrand"].asString)
                    if (jsonValue.has("AutoReconnectDelay")) delay = jsonValue["AutoReconnectDelay"].asInt
                }
                key.equals("liquidchat", true) -> {
                    val jsonValue = value as JsonObject
                    }
                key.equals("clientConfiguration", true) -> {
                    val jsonValue = value as JsonObject
                    if (jsonValue.has("EnabledClientTitle")) enabledClientTitle =
                        jsonValue["EnabledClientTitle"].asBoolean
                    if (jsonValue.has("StylisedAlts")) stylisedAlts = jsonValue["StylisedAlts"].asBoolean
                    if (jsonValue.has("AltsLength")) altsLength = jsonValue["AltsLength"].asInt
                    if (jsonValue.has("CleanAlts")) unformattedAlts = jsonValue["CleanAlts"].asBoolean
                    if (jsonValue.has("AltsPrefix")) altsPrefix = jsonValue["AltsPrefix"].asString
                    if (jsonValue.has("OverrideLanguage")) overrideLanguage = jsonValue["OverrideLanguage"].asString
                    if (jsonValue.has("LastUsername")) SessionStorage.lastUsername = jsonValue["LastUsername"].asString
                    SessionStorage.applySavedUsername()
                }
                else -> {
                    val module = moduleManager[key] ?: continue

                    val jsonModule = value as JsonObject
                    for (moduleValue in module.values) {
                        val element = jsonModule[moduleValue.name]
                        if (element != null) moduleValue.fromJson(element)
                    }
                }
            }
        }
    }

    /**
     * Save config to file
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun saveConfig() {
        val jsonObject = JsonObject()
        jsonObject.run {
            addProperty("CommandPrefix", commandManager.prefix)
        }

        val jsonTargets = JsonObject()
        jsonTargets.run {
            addProperty("TargetPlayer", playerValue)
            addProperty("TargetMobs", mobValue)
            addProperty("TargetAnimals", animalValue)
            addProperty("TargetInvisible", invisibleValue)
            addProperty("TargetDead", deadValue)
        }

        jsonObject.add("targets", jsonTargets)
        val jsonFeatures = JsonObject()
        jsonFeatures.run {
            addProperty("AntiForge", fmlFixesEnabled)
            addProperty("AntiForgeFML", blockFML)
            addProperty("AntiForgeProxy", blockProxyPacket)
            addProperty("AntiForgePayloads", blockPayloadPackets)
            addProperty("FixResourcePackExploit", blockResourcePackExploit)
            addProperty("ConnectToRouter", ConnectToRouter.persistedEnabled)
            addProperty("RouterPhonePassword", ConnectToRouter.phonePassword)
            addProperty("AntiSpawnLag", AntiSpawnLag.enabled)
            addProperty("LazyChunks", LazyChunkCache.enabled)
            addProperty("AntiTranslucent", AntiTranslucent.enabled)
            addProperty("NoChatEffects", NoChatEffects.enabled)
            addProperty("NoTitle", NoTitle.enabled)
            addProperty("ClientBrand", possibleBrands.get())
            addProperty("AutoReconnectDelay", delay)
        }
        jsonObject.add("features", jsonFeatures)

        val liquidChatObject = JsonObject()
        jsonObject.add("liquidchat", liquidChatObject)

        val clientObject = JsonObject()
        val usernameForSave = SessionStorage.getUsernameForSave()
        clientObject.run {
            addProperty("EnabledClientTitle", enabledClientTitle)
            addProperty("StylisedAlts", stylisedAlts)
            addProperty("AltsLength", altsLength)
            addProperty("CleanAlts", unformattedAlts)
            addProperty("AltsPrefix", altsPrefix)
            addProperty("OverrideLanguage", overrideLanguage)
            addProperty("LastUsername", usernameForSave)
        }
        jsonObject.add("clientConfiguration", clientObject)

        for (module in moduleManager) {
            if (module.values.isEmpty()) continue

            val jsonModule = JsonObject()
            for (value in module.values) jsonModule.add(value.name, value.toJson())
            jsonObject.add(module.name, jsonModule)
        }

        file.writeText(PRETTY_GSON.toJson(jsonObject))
    }
}
