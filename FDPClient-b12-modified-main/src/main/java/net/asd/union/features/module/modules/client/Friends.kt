/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.FDPClient
import net.asd.union.config.ListValue
import net.asd.union.config.TextValue
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.file.FileManager.friendsConfig
import net.asd.union.utils.client.ClientUtils.LOGGER
import org.lwjgl.input.Keyboard
import java.util.concurrent.ConcurrentHashMap

object Friends : Module("Friends", Category.CLIENT, Keyboard.KEY_NONE, hideModule = false) {

    private val addFriendValue = TextValue("AddFriend", "")

    private val friends = ConcurrentHashMap<String, Boolean>()

    override fun onEnable() {
        loadFriends()
    }

    fun addFriend(name: String) {
        if (name.isEmpty() || name.isBlank()) return

        // Only allow player names, not other entities
        if (name.contains(" ")) return // Player names don't contain spaces

        if (friendsConfig.addFriend(name, name)) {
            friends[name] = true
            FDPClient.fileManager.saveConfig(friendsConfig)
            LOGGER.info("Added friend: $name")
        }
    }

    fun removeFriend(name: String) {
        if (friendsConfig.removeFriend(name)) {
            friends.remove(name)
            FDPClient.fileManager.saveConfig(friendsConfig)
            LOGGER.info("Removed friend: $name")
        }
    }

    fun isFriend(name: String): Boolean {
        return friendsConfig.isFriend(name)
    }

    fun getFriends(): Set<String> {
        return friendsConfig.friends.map { it.playerName }.toSet()
    }
    
    private fun loadFriends() {
        friends.clear()
        friendsConfig.friends.forEach { friend ->
            friends[friend.playerName] = true
        }
    }

    fun clearFriends() {
        friendsConfig.clearFriends()
        friends.clear()
        FDPClient.fileManager.saveConfig(friendsConfig)
    }
}