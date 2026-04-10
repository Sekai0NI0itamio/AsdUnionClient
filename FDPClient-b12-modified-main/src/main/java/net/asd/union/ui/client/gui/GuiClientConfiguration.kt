/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.client.gui

import net.asd.union.FDPClient.clientTitle
import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.handler.lang.LanguageManager
import net.asd.union.handler.lang.translationMenu
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.MinecraftInstance.Companion.mc
import net.asd.union.utils.io.FileFilters
import net.asd.union.utils.io.MiscUtils
import net.asd.union.utils.io.MiscUtils.showErrorPopup
import net.asd.union.utils.render.IconUtils
import net.asd.union.utils.render.RenderUtils.drawBloom
import net.asd.union.utils.render.shader.Background
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraftforge.fml.client.config.GuiSlider
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.Display
import java.awt.Color

class GuiClientConfiguration(val prevGui: GuiScreen) : AbstractScreen() {

    companion object {
        var enabledClientTitle = true
        var stylisedAlts = true
        var unformattedAlts = false
        var altsLength = 16
        var altsPrefix = ""

        fun updateClientWindow() {
            if (enabledClientTitle) {
                // Set LiquidBounce title
                Display.setTitle(clientTitle)
                // Update favicon
                IconUtils.getFavicon()?.let { icons ->
                    Display.setIcon(icons)
                }
            } else {
                // Set original title
                Display.setTitle("Minecraft 1.8.9")
                // Update favicon
                mc.setWindowIcon()
            }
        }

    }

    private lateinit var languageButton: GuiButton

    private lateinit var altsModeButton: GuiButton
    private lateinit var unformattedAltsButton: GuiButton
    private lateinit var altsSlider: GuiSlider

    private lateinit var titleButton: GuiButton

    private lateinit var altPrefixField: GuiTextField

    override fun initGui() {
        // Title button
        // Location > 1st row
        val buttonSpacing = 30 // Consistent spacing between elements

        titleButton = +GuiButton(
            4, width / 2 - 100, height / 4 + 25, "Client title (${if (enabledClientTitle) "On" else "Off"})"
        )

        languageButton = +GuiButton(
            7,
            width / 2 - 100,
            height / 4 + 25 + buttonSpacing,
            "Language (${LanguageManager.overrideLanguage.ifBlank { "Game" }})"
        )


        // AltManager configuration buttons
        // Location > 3rd row
        altsModeButton = +GuiButton(
            6,
            width / 2 - 100,
            height / 4 + 100, // Start of AltManager section after section header
            "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
        )

        altsSlider = +GuiSlider(
            -1,
            width / 2 - 100,
            height / 4 + 100 + buttonSpacing * 1,
            200,
            20,
            "${if (stylisedAlts && unformattedAlts) "Random alt max" else "Random alt"} length (",
            ")",
            6.0,
            16.0,
            altsLength.toDouble(),
            false,
            true
        ) {
            altsLength = it.valueInt
        }

        unformattedAltsButton = +GuiButton(
            5,
            width / 2 - 100,
            height / 4 + 100 + buttonSpacing * 2,
            "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
        ).also {
            it.enabled = stylisedAlts
        }

        altPrefixField = GuiTextField(2, Fonts.font35, width / 2 - 100, height / 4 + 100 + buttonSpacing * 3, 200, 20)
        altPrefixField.maxStringLength = 16

        // Back button
        +GuiButton(8, width / 2 - 100, height / 4 + 100 + buttonSpacing * 5, "Back")
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            4 -> {
                enabledClientTitle = !enabledClientTitle
                titleButton.displayString = "Client title (${if (enabledClientTitle) "On" else "Off"})"
                updateClientWindow()
            }

            5 -> {
                unformattedAlts = !unformattedAlts
                unformattedAltsButton.displayString = "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
                altsSlider.dispString = "${if (unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
            }

            6 -> {
                stylisedAlts = !stylisedAlts
                altsModeButton.displayString = "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
                altsSlider.dispString =
                    "${if (stylisedAlts && unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
                unformattedAltsButton.enabled = stylisedAlts
            }


            7 -> {
                val languageIndex = LanguageManager.knownLanguages.indexOf(LanguageManager.overrideLanguage)

                // If the language is not found, set it to the first language
                if (languageIndex == -1) {
                    LanguageManager.overrideLanguage = LanguageManager.knownLanguages.first()
                } else {
                    // If the language is the last one, set it to blank
                    if (languageIndex == LanguageManager.knownLanguages.size - 1) {
                        LanguageManager.overrideLanguage = ""
                    } else {
                        // Otherwise, set it to the next language
                        LanguageManager.overrideLanguage = LanguageManager.knownLanguages[languageIndex + 1]
                    }
                }

                initGui()
            }

            8 -> mc.displayGuiScreen(prevGui)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        Fonts.fontBold180.drawCenteredString(
            translationMenu("configuration"), width / 2F, height / 8F + 5F, 4673984, true
        )

        Fonts.font40.drawString(
            "Window", width / 2F - 98F, height / 4F + 15F, 0xFFFFFF, true
        )


        Fonts.font40.drawString(
            translationMenu("altManager"), width / 2F - 98F, height / 4F + 90F, 0xFFFFFF, true
        )

        altPrefixField.drawTextBox()
        if (altPrefixField.text.isEmpty() && !altPrefixField.isFocused) {
            Fonts.font35.drawStringWithShadow(
                altsPrefix.ifEmpty { translationMenu("altManager.typeCustomPrefix") },
                altPrefixField.xPosition + 4f,
                altPrefixField.yPosition + (altPrefixField.height - Fonts.font35.FONT_HEIGHT) / 2F,
                0xffffff
            )
        }

        drawBloom(mouseX - 5, mouseY - 5, 10, 10, 16, Color(guiColor))

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        if (altPrefixField.isFocused) {
            altPrefixField.textboxKeyTyped(typedChar, keyCode)
            altsPrefix = altPrefixField.text
            saveConfig(valuesConfig)
        }

        super.keyTyped(typedChar, keyCode)
    }

    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        altPrefixField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}