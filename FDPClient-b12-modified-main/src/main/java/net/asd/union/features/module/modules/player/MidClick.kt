/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player

import net.asd.union.event.Render2DEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.file.FileManager.friendsConfig
import net.asd.union.file.FileManager.saveConfig
import net.asd.union.utils.client.chat
import net.asd.union.utils.render.ColorUtils.stripColor
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.input.Mouse

object MidClick : Module("MidClick", Category.PLAYER, subjective = true, gameDetecting = false, hideModule = false) {
    private var wasDown = false


    val onRender = handler<Render2DEvent> {
        if (mc.currentScreen != null)
            return@handler

        if (!wasDown && Mouse.isButtonDown(2)) {
            val entity = mc.objectMouseOver.entityHit

            if (entity is EntityPlayer) {
                val playerName = stripColor(entity.name)

                if (!friendsConfig.isFriend(playerName)) {
                    friendsConfig.addFriend(playerName)
                    saveConfig(friendsConfig)
                    chat("§a§l$playerName§c was added to your friends.")
                } else {
                    friendsConfig.removeFriend(playerName)
                    saveConfig(friendsConfig)
                    chat("§a§l$playerName§c was removed from your friends.")
                }

            } else
                chat("§c§lError: §aYou need to select a player.")
        }
        wasDown = Mouse.isButtonDown(2)
    }
}