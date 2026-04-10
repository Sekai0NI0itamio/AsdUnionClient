/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.client

import net.asd.union.features.module.Module
import net.asd.union.features.module.Category
import net.asd.union.config.boolean

object ChatControl : Module("ChatControl", Category.CLIENT, gameDetecting = false, hideModule = false, subjective = true) {

    init {
        state = true
    }

    val chatLimitValue by boolean("NoChatLimit", true)
    val chatClearValue by boolean("NoChatClear", true)
    private val fontChat by boolean("FontChat", false)

    fun shouldModifyChatFont() = handleEvents() && fontChat
}