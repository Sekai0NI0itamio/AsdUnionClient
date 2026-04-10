/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.render

object NoChatEffects {
    var enabled = false

    private val legacyFormattingRegex = Regex("(?i)[§&$][0-9A-FK-OR]")
    private val legacyHexFormattingRegex = Regex("(?i)[§&$]x(?:[§&$][0-9A-F]){6}")
    private val strayPrefixRegex = Regex("[§&$]")

    fun sanitizeForRender(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val stripped = strayPrefixRegex.replace(
            legacyFormattingRegex.replace(
                legacyHexFormattingRegex.replace(normalized, ""),
                ""
            ),
            ""
        )

        if (stripped.isEmpty()) {
            return "§7"
        }

        return stripped
            .split('\n')
            .joinToString("\n") { line -> "§7$line" }
    }
}
