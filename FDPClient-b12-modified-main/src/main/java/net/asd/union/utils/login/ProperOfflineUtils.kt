/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.login

import net.asd.union.event.EventManager.call
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.util.Session

/**
 * Utilities for "Proper Offline Accounts".
 *
 * When this feature is enabled, account creation/login will attempt to resolve
 * the username's real Mojang UUID via the Mojang API. This means the client
 * presents the correct UUID to servers that perform UUID-based checks, allowing
 * the user to join some servers that would otherwise reject a purely offline UUID.
 *
 * The access token remains a placeholder ("-") because we cannot obtain a real
 * auth token without actual credentials — this is still an offline session, just
 * with the correct UUID attached.
 */
object ProperOfflineUtils : MinecraftInstance {

    /**
     * Attempt to apply a "proper offline" session for [username].
     *
     * Looks up the real Mojang UUID for the username. If found, sets the session
     * with that UUID and returns [ApplyResult.SUCCESS]. If the username has no
     * associated Mojang account, falls back to the standard offline UUID and
     * returns [ApplyResult.NO_MOJANG_ACCOUNT]. On network/parse failure returns
     * [ApplyResult.LOOKUP_FAILED].
     *
     * This call performs a network request and must NOT be called on the main thread.
     */
    fun applyProperOfflineSession(username: String): ApplyResult {
        if (username.isBlank()) return ApplyResult.LOOKUP_FAILED

        return try {
            val uuid = UserUtils.getUUID(username)
            if (uuid == null) {
                LOGGER.info("[ProperOffline] No Mojang UUID found for '$username', using offline UUID.")
                ApplyResult.NO_MOJANG_ACCOUNT
            } else {
                // Format UUID with dashes (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
                val formattedUuid = formatUuid(uuid)
                mc.addScheduledTask {
                    mc.session = Session(username, formattedUuid, "-", "legacy")
                    call(SessionUpdateEvent)
                }
                LOGGER.info("[ProperOffline] Applied proper offline session for '$username' with UUID $formattedUuid.")
                ApplyResult.SUCCESS
            }
        } catch (e: Exception) {
            LOGGER.error("[ProperOffline] Failed to look up UUID for '$username'.", e)
            ApplyResult.LOOKUP_FAILED
        }
    }

    /**
     * Insert dashes into a raw 32-char UUID hex string if they are missing.
     */
    private fun formatUuid(raw: String): String {
        if (raw.contains('-')) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }

    enum class ApplyResult {
        /** Real Mojang UUID was found and applied. */
        SUCCESS,
        /** Username exists but has no associated Mojang account (e.g. name-squatted or purely offline). */
        NO_MOJANG_ACCOUNT,
        /** Network or parse error during lookup. */
        LOOKUP_FAILED
    }
}
