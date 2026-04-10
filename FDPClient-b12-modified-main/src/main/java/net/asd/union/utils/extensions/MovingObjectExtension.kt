/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.extensions

import net.minecraft.util.MovingObjectPosition.MovingObjectType

val MovingObjectType.isMiss
    get() = this == MovingObjectType.MISS

val MovingObjectType.isBlock
    get() = this == MovingObjectType.BLOCK

val MovingObjectType.isEntity
    get() = this == MovingObjectType.ENTITY