/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.longjumpmodes

import net.asd.union.event.JumpEvent
import net.asd.union.event.MoveEvent
import net.asd.union.utils.client.MinecraftInstance

open class LongJumpMode(val modeName: String) : MinecraftInstance {
    open fun onUpdate() {}
    open fun onMove(event: MoveEvent) {}
    open fun onJump(event: JumpEvent) {}

    open fun onEnable() {}
    open fun onDisable() {}
}