package net.asd.union.ui.client.gui

import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.render.AntiSpawnLag
import net.asd.union.handler.render.AntiTranslucent
import net.asd.union.handler.render.LazyChunkCache
import net.asd.union.handler.render.NoChatEffects
import net.asd.union.handler.render.NoTitle
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import java.io.IOException

class GuiOptimize(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var lazyChunksButton: GuiButton
    private lateinit var antiTranslucentButton: GuiButton
    private lateinit var noChatEffectsButton: GuiButton
    private lateinit var antiSpawnLagButton: GuiButton
    private lateinit var noTitleButton: GuiButton

    override fun initGui() {
        lazyChunksButton =
            +GuiButton(1, width / 2 - 100, height / 4 + 35, "Lazy Chunks (${if (LazyChunkCache.enabled) "On" else "Off"})")
        antiTranslucentButton = +GuiButton(
            2,
            width / 2 - 100,
            height / 4 + 60,
            "Anti Translucent (${if (AntiTranslucent.enabled) "On" else "Off"})",
        )
        noChatEffectsButton = +GuiButton(
            3,
            width / 2 - 100,
            height / 4 + 85,
            "NoChatEffects (${if (NoChatEffects.enabled) "On" else "Off"})",
        )
        antiSpawnLagButton = +GuiButton(
            4,
            width / 2 - 100,
            height / 4 + 110,
            "AntiSpawnLag (${if (AntiSpawnLag.enabled) "On" else "Off"})",
        )
        noTitleButton =
            +GuiButton(5, width / 2 - 100, height / 4 + 135, "NoTitle (${if (NoTitle.enabled) "On" else "Off"})")
        +GuiButton(0, width / 2 - 100, height / 4 + 180, "Back")
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> {
                LazyChunkCache.enabled = !LazyChunkCache.enabled
                lazyChunksButton.displayString = "Lazy Chunks (${if (LazyChunkCache.enabled) "On" else "Off"})"
            }
            2 -> {
                AntiTranslucent.enabled = !AntiTranslucent.enabled
                antiTranslucentButton.displayString =
                    "Anti Translucent (${if (AntiTranslucent.enabled) "On" else "Off"})"
            }
            3 -> {
                NoChatEffects.enabled = !NoChatEffects.enabled
                noChatEffectsButton.displayString =
                    "NoChatEffects (${if (NoChatEffects.enabled) "On" else "Off"})"
            }
            4 -> {
                AntiSpawnLag.enabled = !AntiSpawnLag.enabled
                antiSpawnLagButton.displayString = "AntiSpawnLag (${if (AntiSpawnLag.enabled) "On" else "Off"})"
            }
            5 -> {
                NoTitle.enabled = !NoTitle.enabled
                noTitleButton.displayString = "NoTitle (${if (NoTitle.enabled) "On" else "Off"})"
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile = true
        drawBackground(0)
        Fonts.fontBold180.drawCenteredString("Optimize", width / 2f, height / 8f + 5f, 4673984, true)
        assumeNonVolatile = false

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}
