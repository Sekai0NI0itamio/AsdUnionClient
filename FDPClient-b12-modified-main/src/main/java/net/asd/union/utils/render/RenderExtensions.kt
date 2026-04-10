/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.render

import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer

inline fun drawWithTessellatorWorldRenderer(drawAction: WorldRenderer.() -> Unit) {
    val instance = Tessellator.getInstance()
    try {
        instance.worldRenderer.drawAction()
    } finally {
        instance.draw()
    }
}