/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.visual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import net.asd.union.config.block
import net.asd.union.config.choices
import net.asd.union.config.color
import net.asd.union.config.int
import net.asd.union.event.Render3DEvent
import net.asd.union.event.handler
import net.asd.union.event.loopHandler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.block.BlockUtils.getBlockName
import net.asd.union.utils.block.BlockUtils.searchBlocks
import net.asd.union.utils.block.block
import net.asd.union.utils.extensions.component1
import net.asd.union.utils.extensions.component2
import net.asd.union.utils.extensions.component3
import net.asd.union.utils.extensions.eyes
import net.asd.union.utils.render.RenderUtils.draw2D
import net.asd.union.utils.render.RenderUtils.drawBlockBox
import net.minecraft.block.Block
import net.minecraft.init.Blocks.air
import net.minecraft.util.BlockPos
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object BlockESP : Module("BlockESP", Category.VISUAL, hideModule = false) {
    private val mode by choices("Mode", arrayOf("Box", "2D"), "Box")
    private val block by block("Block", 168)
    private val radius by int("Radius", 40, 5..120)
    private val blockLimit by int("BlockLimit", 256, 0..2056)

    private val color by color("Color", Color(255, 179, 72))

    private val posList = ConcurrentHashMap.newKeySet<BlockPos>()

    override fun onDisable() {
        posList.clear()
    }

    val onSearch = loopHandler(dispatcher = Dispatchers.Default) {
        val selectedBlock = Block.getBlockById(block)

        if (selectedBlock == null || selectedBlock == air) {
            delay(1000)
            return@loopHandler
        }

        val (x, y, z) = mc.thePlayer.eyes
        val radiusSq = radius * radius

        posList.removeIf {
            it.distanceSqToCenter(x, y, z) >= radiusSq || it.block != selectedBlock
        }

        val listSpace = blockLimit - posList.size

        if (listSpace > 0) {
            posList += searchBlocks(radius, setOf(selectedBlock), listSpace).keys
        }

        delay(1000)
    }

    val onRender3D = handler<Render3DEvent> {
        when (mode) {
            "Box" -> posList.forEach { drawBlockBox(it, color, true) }
            "2D" -> posList.forEach { draw2D(it, color.rgb, Color.BLACK.rgb) }
        }
    }

    override val tag
        get() = getBlockName(block)
}