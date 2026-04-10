/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module

enum class Category(val displayName: String, val configName: String, val htmlIcon: String, initialPosX: Int, initialPosY: Int, val clicked: Boolean = false, val showMods: Boolean = true) {
    COMBAT("Combat", "Combat", "&#xe000;", 15, 15),
    PLAYER("Player", "Player", "&#xe7fd;", 15, 180),
    MOVEMENT("Movement", "Movement", "&#xe566;", 330, 15),
    VISUAL("Visual", "Visual", "&#xe417;", 225, 15),
    CLIENT("Client", "Client", "&#xe869;", 15, 330),
    OTHER("Other", "Other", "&#xe5d3;", 15, 330),
    EXPLOIT("Exploit", "Exploit", "&#xe868;", 120, 180);

    var posX: Int = initialPosX
    var posY: Int = initialPosY
}
