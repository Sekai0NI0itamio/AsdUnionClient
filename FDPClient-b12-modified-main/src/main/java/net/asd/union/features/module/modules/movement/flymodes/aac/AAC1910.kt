/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.movement.flymodes.aac

import net.asd.union.event.Render3DEvent
import net.asd.union.features.module.modules.movement.Flight.onRender3D
import net.asd.union.features.module.modules.movement.Flight.aacSpeed
import net.asd.union.features.module.modules.movement.Flight.startY
import net.asd.union.features.module.modules.movement.flymodes.FlyMode
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.movement.MovementUtils.strafe
import net.asd.union.utils.render.RenderUtils.drawPlatform
import net.minecraft.network.play.client.C03PacketPlayer
import java.awt.Color

object AAC1910 : FlyMode("AAC1.9.10") {

    private var jump = 0.0

    override fun onEnable() {
        jump = 3.8
    }

    override fun onUpdate() {
        if (mc.gameSettings.keyBindJump.isKeyDown)
            jump += 0.2

        if (mc.gameSettings.keyBindSneak.isKeyDown)
            jump -= 0.2

        if (startY + jump > mc.thePlayer.posY) {
            sendPacket(C03PacketPlayer(true))
            mc.thePlayer.motionY = 0.8
            strafe(aacSpeed)
        }

        // TODO: Doesn't this always overwrite the strafe(aacSpeed)?
        strafe()
    }

    override fun onRender3D(event: Render3DEvent) {
        drawPlatform(startY + jump, Color(0, 0, 255, 90), 1.0)
    }
}
