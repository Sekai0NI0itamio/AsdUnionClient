/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.handler.api

import net.asd.union.utils.client.ClientUtils.LOGGER

fun reloadMessageOfTheDay() {
    // MOTD functionality disabled by user request
    // try {
    //     messageOfTheDay = ClientApi.getMessageOfTheDay()
    // } catch (e: Exception) {
    //     LOGGER.error("Unable to receive message of the day", e)
    // }
}

var messageOfTheDay: MessageOfTheDay? = null
    private set