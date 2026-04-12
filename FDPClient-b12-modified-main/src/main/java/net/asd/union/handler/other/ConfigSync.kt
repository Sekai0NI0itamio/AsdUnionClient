/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.other

import net.asd.union.event.Listenable
import net.asd.union.event.WorldEvent
import net.asd.union.event.handler
import net.asd.union.file.FileManager
import net.asd.union.file.FileManager.clickGuiConfig
import net.asd.union.file.FileManager.hudConfig
import net.asd.union.file.FileManager.modulesConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.utils.client.ClientUtils

object ConfigSync : Listenable {
    private var lastWorldPresent = false

    private val onWorld = handler<WorldEvent> { event ->
        val hasWorld = event.worldClient != null

        if (hasWorld != lastWorldPresent) {
            runCatching {
                FileManager.saveConfig(modulesConfig, false)
                FileManager.saveConfig(valuesConfig, false)
                FileManager.saveConfig(clickGuiConfig, false)
                FileManager.saveConfig(hudConfig, false)
            }.onFailure { e ->
                ClientUtils.LOGGER.warn("[ConfigSync] Failed to flush configs on world change: ${e.message}")
            }
        }

        lastWorldPresent = hasWorld
    }
}
