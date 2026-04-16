/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.FDPClient
import net.asd.union.config.boolean
import net.asd.union.config.ListValue
import net.asd.union.config.TextValue
import net.asd.union.event.Render3DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.file.FileManager.friendsConfig
import net.asd.union.utils.extensions.hitBox
import net.asd.union.utils.extensions.isClientFriend
import net.asd.union.utils.render.RenderUtils
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.util.concurrent.ConcurrentHashMap

object Friends : Module("Friends", Category.CLIENT, Keyboard.KEY_NONE, hideModule = false) {

    private val addFriendValue = TextValue("AddFriend", "")

    private val friends = ConcurrentHashMap<String, Boolean>()

    private val renderEspValue = boolean("RenderESP", false)
    private val renderEsp by renderEspValue
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
        }
    }

    fun removeFriend(name: String) {
        if (friendsConfig.removeFriend(name)) {
            friends.remove(name)
            FDPClient.fileManager.saveConfig(friendsConfig)
        }
    }

    fun isFriend(name: String): Boolean {
        return friendsConfig.isFriend(name)
    }

    fun getFriends(): List<String> {
        return friendsConfig.friends.map { it.playerName }.sorted()
    }

    val onRender3D = handler<Render3DEvent> { event ->
        if (!state || !renderEsp) return@handler

        val world = mc.theWorld ?: return@handler
        val localPlayer = mc.thePlayer ?: return@handler
        val renderManager = mc.renderManager
        val partial = event.partialTicks.toDouble()

        glPushMatrix()
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        for (entity in world.loadedEntityList) {
            val friend = entity as? EntityPlayer ?: continue
            if (friend == localPlayer || !friend.isEntityAlive || !friend.isClientFriend()) continue

            val hitBox = friend.hitBox
            val interpolatedX = friend.lastTickPosX + (friend.posX - friend.lastTickPosX) * partial
            val interpolatedY = friend.lastTickPosY + (friend.posY - friend.lastTickPosY) * partial
            val interpolatedZ = friend.lastTickPosZ + (friend.posZ - friend.lastTickPosZ) * partial

            val offsetX = interpolatedX - friend.posX - renderManager.renderPosX
            val offsetY = interpolatedY - friend.posY - renderManager.renderPosY
            val offsetZ = interpolatedZ - friend.posZ - renderManager.renderPosZ

            val adjustedBox = hitBox.offset(offsetX, offsetY, offsetZ)

            glColor4f(0.15f, 0.35f, 1f, 0.18f)
            RenderUtils.drawFilledBox(adjustedBox)
            glColor4f(0.15f, 0.35f, 1f, 1f)
            glLineWidth(2f)
            RenderUtils.drawSelectionBoundingBox(adjustedBox)
        }

        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)

        glPopAttrib()
        glPopMatrix()
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