/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.nowebmodes

import net.asd.union.utils.client.MinecraftInstance

open class NoWebMode(val modeName: String) : MinecraftInstance {
	open fun onUpdate() {}
}
