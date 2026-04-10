/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.font.fontmanager.api

fun interface FontManager {
    fun fontFamily(name: String): FontFamily

    fun font(name: String, size: Int): FontRenderer =
        fontFamily(name).ofSize(size)
}
