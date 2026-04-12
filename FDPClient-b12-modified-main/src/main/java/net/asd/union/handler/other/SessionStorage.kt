package net.asd.union.handler.other

import net.asd.union.event.Listenable
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.event.handler
import net.asd.union.file.FileManager
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.util.Session
import java.util.UUID

object SessionStorage : Listenable, MinecraftInstance {
    var lastUsername = ""

    fun applySavedUsername() {
        val username = lastUsername.trim()
        if (username.isEmpty()) {
            return
        }

        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
        mc.session = Session(username, uuid.toString(), "-", "legacy")
    }

    fun getUsernameForSave(): String {
        val currentUsername = mc.session?.username?.trim().orEmpty()
        if (currentUsername.isNotEmpty()) {
            lastUsername = currentUsername
            return currentUsername
        }

        return lastUsername
    }

    val onSessionUpdate = handler<SessionUpdateEvent> {
        val username = mc.session?.username?.trim().orEmpty()
        if (username.isEmpty()) {
            return@handler
        }

        lastUsername = username
        FileManager.saveConfig(FileManager.valuesConfig, false)
    }
}
