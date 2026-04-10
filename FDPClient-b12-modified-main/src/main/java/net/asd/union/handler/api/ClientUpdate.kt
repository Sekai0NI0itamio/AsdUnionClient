/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.api

import net.asd.union.FDPClient
import net.asd.union.FDPClient.IN_DEV
import net.asd.union.FDPClient.clientVersionNumber
import net.asd.union.utils.client.ClientUtils.LOGGER
import java.text.SimpleDateFormat
import java.util.*

object ClientUpdate {

    val gitInfo = Properties().also {
        val inputStream = FDPClient::class.java.classLoader.getResourceAsStream("git.properties")

        if (inputStream != null) {
            it.load(inputStream)
        } else {
            it["git.build.version"] = "unofficial"
        }
    }

    fun reloadNewestVersion() {
        // Update checking disabled by user request
        // https://api.liquidbounce.net/api/v1/version/builds/legacy
        // try {
        //     newestVersion = ClientApi.getNewestBuild(release = !IN_DEV)
        // } catch (e: Exception) {
        //     LOGGER.error("Unable to receive update information", e)
        // }
    }

    var newestVersion: Build? = null
        private set

    fun hasUpdate(): Boolean {
        // Update checking disabled by user request
        return false
    }

}

