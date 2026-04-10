/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.io

import net.asd.union.FDPClient
import java.util.*

class GitUtils {
    companion object {
        @JvmField
        val gitInfo = Properties().also {
            val inputStream = FDPClient::class.java.classLoader.getResourceAsStream("git.properties")
            if (inputStream != null) {
                it.load(inputStream)
            } else {
                it["git.branch"] = "master"
            }
        }

        @JvmField
        val gitBranch = (gitInfo["git.branch"] ?: "unknown")
    }
}