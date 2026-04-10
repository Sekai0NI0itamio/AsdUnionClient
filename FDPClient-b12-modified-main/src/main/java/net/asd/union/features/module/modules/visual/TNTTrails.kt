/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import net.asd.union.event.Render3DEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.minecraft.entity.item.EntityTNTPrimed
import org.lwjgl.opengl.GL11

object TNTTrails : Module("TNTTrails", Category.VISUAL, spacedName = "TNT Trails", hideModule = false) {
    private val tntPositions = mutableMapOf<EntityTNTPrimed, MutableList<Triple<Double, Double, Double>>>()


    val onRender3D = handler<Render3DEvent> {
        tntPositions.keys.toList().forEach { tnt ->
            val positions = tntPositions[tnt] ?: return@forEach

            GL11.glPushMatrix()
            GL11.glLineWidth(2.0f)
            GL11.glBegin(GL11.GL_LINE_STRIP)
            GL11.glColor3f(1.0f, 0.0f, 0.0f)

            for (pos in positions) {
                GL11.glVertex3d(pos.first - mc.renderManager.viewerPosX, pos.second - mc.renderManager.viewerPosY, pos.third - mc.renderManager.viewerPosZ)
            }

            GL11.glEnd()
            GL11.glPopMatrix()
        }
    }

    val onUpdate = handler<UpdateEvent> {
        mc.theWorld.loadedEntityList.filterIsInstance<EntityTNTPrimed>().forEach { tnt ->
            val positions = tntPositions.getOrPut(tnt) { mutableListOf() }
            positions.add(Triple(tnt.posX, tnt.posY, tnt.posZ))
        }
    }
}