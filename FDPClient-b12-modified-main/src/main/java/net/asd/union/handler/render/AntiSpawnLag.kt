/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.render

import net.asd.union.event.Listenable
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.modules.exploit.Ghost
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.gui.GuiGameOver

object AntiSpawnLag : Listenable, MinecraftInstance {
    private const val BYPASS_WINDOW_MS = 5_000L

    @Volatile
    private var skipNextTerrainScreen = false

    @Volatile
    private var skipNextWorldReload = false

    @Volatile
    private var bypassExpiresAt = 0L

    var enabled = false
        set(value) {
            field = value

            if (!value) {
                clearBypassState()
            }
        }

    private fun clearBypassState() {
        skipNextTerrainScreen = false
        skipNextWorldReload = false
        bypassExpiresAt = 0L
    }

    private fun canBypass(): Boolean {
        val canBypass = enabled && bypassExpiresAt > System.currentTimeMillis()

        if (!canBypass) {
            clearBypassState()
        }

        return canBypass
    }

    private fun markFastRespawnCycle() {
        skipNextTerrainScreen = true
        skipNextWorldReload = true
        bypassExpiresAt = System.currentTimeMillis() + BYPASS_WINDOW_MS
    }

    fun consumeTerrainScreenBypass(): Boolean {
        if (!canBypass() || !skipNextTerrainScreen) {
            return false
        }

        skipNextTerrainScreen = false
        return true
    }

    fun consumeWorldReloadBypass(): Boolean {
        if (!canBypass() || !skipNextWorldReload) {
            return false
        }

        skipNextWorldReload = false
        return true
    }

    val onUpdate = handler<UpdateEvent> {
        if (!enabled || Ghost.handleEvents()) {
            return@handler
        }

        val player = mc.thePlayer ?: return@handler
        val shouldFastRespawn = player.isDead || player.health <= 0f || mc.currentScreen is GuiGameOver

        if (!shouldFastRespawn) {
            return@handler
        }

        markFastRespawnCycle()
        player.respawnPlayer()
        mc.displayGuiScreen(null)
    }

    override fun handleEvents(): Boolean = enabled
}
