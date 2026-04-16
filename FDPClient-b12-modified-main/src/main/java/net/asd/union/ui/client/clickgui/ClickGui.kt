/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.client.clickgui

import kotlinx.coroutines.*
import net.asd.union.FDPClient.CLIENT_NAME
import net.asd.union.FDPClient.moduleManager
import net.asd.union.config.SettingsUtils
import net.asd.union.handler.api.ClientApi
import net.asd.union.handler.api.loadSettings
import net.asd.union.handler.api.autoSettingsList
import net.asd.union.features.module.Category
import net.asd.union.features.module.modules.client.ClickGUIModule
import net.asd.union.features.module.modules.client.ClickGUIModule.scale
import net.asd.union.features.module.modules.client.ClickGUIModule.scrolls
import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.file.FileManager.clickGuiConfig
import net.asd.union.file.FileManager.saveConfig
import net.asd.union.file.FileManager.valuesConfig
import net.asd.union.ui.client.clickgui.elements.ButtonElement
import net.asd.union.ui.client.clickgui.elements.ModuleElement
import net.asd.union.ui.client.clickgui.style.Style
import net.asd.union.ui.client.clickgui.style.styles.BlackStyle
import net.asd.union.ui.client.hud.HUD
import net.asd.union.ui.client.hud.designer.GuiHudDesigner
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import net.asd.union.features.module.modules.other.AutoText
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.utils.client.ClientUtils
import net.asd.union.features.module.modules.client.Friends
import net.asd.union.utils.client.asResourceLocation
import net.asd.union.utils.client.chat
import net.asd.union.utils.client.playSound
import net.asd.union.utils.kotlin.SharedScopes
import net.asd.union.utils.render.RenderUtils.deltaTime
import net.asd.union.utils.render.RenderUtils.drawBloom
import net.asd.union.utils.render.RenderUtils.drawImage
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager.disableLighting
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.glScaled
import java.awt.Color
import kotlin.math.roundToInt

object ClickGui : GuiScreen() {

    val panels = mutableListOf<Panel>()
    private val hudIcon = ResourceLocation("${CLIENT_NAME.lowercase()}/custom_hud_icon.png")
    var style: Style = BlackStyle
    private var mouseX = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }
    private var mouseY = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    private var autoScrollY: Int? = null

    // Used when closing ClickGui using its key bind, prevents it from getting closed instantly after getting opened.
    // Caused by keyTyped being called along with onKey that opens the ClickGui.
    private var ignoreClosing = false

    fun setDefault() {
        panels.clear()

        val width = 100
        val height = 18
        var yPos = 5

        for (category in Category.values()) {
            panels += object : Panel(category.displayName, 100, yPos, width, height, false) {
                override val elements = moduleManager.mapNotNull {
                    it.takeIf { module -> module.category == category }?.let(::ModuleElement)
                }
            }

            yPos += 20
        }

        yPos += 20

        // Settings Panel
        yPos += 20
        panels += setupSettingsPanel(100, yPos, width, height)
    }

    private fun setupSettingsPanel(xPos: Int = 100, yPos: Int, width: Int, height: Int) =
        object : Panel("Auto Settings", xPos, yPos, width, height, false) {

            /**
             * Auto settings list - loads settings only when panel is opened
             */
            override val elements = runBlocking {
                SharedScopes.IO.async {
                    // Load settings only when the panel is accessed
                    loadSettings(true) { settings ->
                        autoSettingsList = settings
                    }
                    
                    autoSettingsList?.map { setting ->
                        ButtonElement(setting.name, { Integer.MAX_VALUE }) {
                            SharedScopes.IO.launch {
                                try {
                                    chat("Loading settings...")

                                    // Load settings and apply them
                                    val settings = ClientApi.getSettingsScript(settingId = setting.settingId)

                                    chat("Applying settings...")
                                    SettingsUtils.applyScript(settings)

                                    chat("§6Settings applied successfully")
                                    HUD.addNotification(Notification("Updated Settings", "!!!", Type.INFO, 60))
                                    mc.playSound("random.anvil_use".asResourceLocation())

                                } catch (e: Exception) {
                                    ClientUtils.LOGGER.error("Failed to load settings", e)
                                    chat("Failed to load settings: ${e.message}")
                                }
                            }
                        }.apply {
                            this.hoverText = buildString {
                                appendLine("§7Description: §e${setting.description.ifBlank { "No description available" }}")
                                appendLine("§7Type: §e${setting.type.displayName}")
                                appendLine("§7Contributors: §e${setting.contributors}")
                                appendLine("§7Last updated: §e${setting.date}")
                                append("§7Status: §e${setting.statusType.displayName} §a(${setting.statusDate})")
                            }
                        }
                    } ?: emptyList()
                }.await()
            }
        }

    override fun drawScreen(x: Int, y: Int, partialTicks: Float) {
        // Enable DisplayList optimization
        assumeNonVolatile {
            mouseX = (x / scale).roundToInt()
            mouseY = (y / scale).roundToInt()

            drawDefaultBackground()
            drawImage(hudIcon, 9, height - 41, 32, 32)

            val scale = scale.toDouble()
            glScaled(scale, scale, scale)

            for (panel in panels) {
                panel.updateFade(deltaTime)
                panel.drawScreenAndClick(mouseX, mouseY)
            }

            descriptions@ for (panel in panels.reversed()) {
                // Don't draw hover text when hovering over a panel header.
                if (panel.isHovered(mouseX, mouseY)) break

                for (element in panel.elements) {
                    if (element is ButtonElement) {
                        if (element.isVisible && element.hoverText.isNotBlank() && element.isHovered(
                                mouseX, mouseY
                            ) && element.y <= panel.y + panel.fade
                        ) {
                            style.drawHoverText(mouseX, mouseY, element.hoverText)
                            // Don't draw hover text for any elements below.
                            break@descriptions
                        }
                    }
                }
            }

            if (Mouse.hasWheel()) {
                val wheel = autoScrollY?.let { it - y } ?: Mouse.getDWheel()
                if (wheel != 0) {
                    var handledScroll = false

                    if (style.handleScroll(mouseX, mouseY, wheel)) {
                        handledScroll = true
                    }

                    // Handle foremost panel.
                    if (!handledScroll) {
                        for (panel in panels.reversed()) {
                            if (panel.handleScroll(mouseX, mouseY, wheel)) {
                                handledScroll = true
                                break
                            }
                        }
                    }

                    if (!handledScroll) handleScroll(wheel)
                }
            }

            disableLighting()
            RenderHelper.disableStandardItemLighting()
            glScaled(1.0, 1.0, 1.0)
        }

        drawBloom(mouseX - 5, mouseY - 5, 10, 10, 16, Color(guiColor))

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun handleScroll(wheel: Int) {
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            scale += wheel * 0.0001f

            for (panel in panels) {
                panel.x = panel.parseX()
                panel.y = panel.parseY()
            }

        } else if (scrolls) {
            for (panel in panels) panel.y = panel.parseY(panel.y + wheel / 10)
        }
    }

    public override fun mouseClicked(x: Int, y: Int, mouseButton: Int) {
        if (mouseButton == 0 && x in 5..50 && y in height - 50..height - 5) {
            mc.displayGuiScreen(GuiHudDesigner())
            return
        }

        if (mouseButton == 2) {
            autoScrollY = y
        }

        mouseX = (x / scale).roundToInt()
        mouseY = (y / scale).roundToInt()

        // Handle foremost panel.
        panels.reversed().forEachIndexed { index, panel ->
            if (panel.mouseClicked(mouseX, mouseY, mouseButton)) return

            panel.drag = false

            if (mouseButton == 0 && panel.isHovered(mouseX, mouseY)) {
                panel.x2 = panel.x - mouseX
                panel.y2 = panel.y - mouseY
                panel.drag = true

                // Move dragged panel to top.
                panels.removeAt(panels.lastIndex - index)
                panels += panel
                return
            }
        }
    }

    public override fun mouseReleased(x: Int, y: Int, button: Int) {
        mouseX = (x / scale).roundToInt()
        mouseY = (y / scale).roundToInt()

        if (button == 2) {
            autoScrollY = null
        }

        for (panel in panels) panel.mouseReleased(mouseX, mouseY, button)
    }

    override fun updateScreen() {
        if (style is BlackStyle) {
            for (panel in panels) {
                for (element in panel.elements) {
                    if (element is ButtonElement) element.hoverTime += if (element.isHovered(mouseX, mouseY)) 1 else -1

                    if (element is ModuleElement) element.slowlyFade += if (element.module.state) 20 else -20
                }
            }
        }

        style.activeTextField?.updateCursorCounter()

        super.updateScreen()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // Handle text input for active TextValue
        val activeTextValue = style.activeTextValue
        val activeTextField = style.activeTextField

        if (activeTextValue != null && activeTextField != null) {
            val specialAutoTextInput = activeTextValue.name == "AddFriend" || activeTextValue.name == "AddMessage" || AutoText.getEditingMessageId() != null

            when (keyCode) {
                Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                    val currentValue = activeTextField.getText()

                    when {
                        activeTextValue.name == "AddFriend" -> {
                            if (currentValue.isNotBlank()) {
                                Friends.addFriend(currentValue.trim())
                            }

                            activeTextValue.set("", false)
                        }

                        activeTextValue.name == "AddMessage" -> {
                            if (currentValue.isNotBlank()) {
                                val editingMessageId = AutoText.getEditingMessageId()
                                if (editingMessageId != null) {
                                    AutoText.updateMessage(editingMessageId, currentValue.trim(), false)
                                } else {
                                    AutoText.addMessage(currentValue.trim(), false)
                                }

                                saveConfig(valuesConfig)
                            }

                            activeTextValue.set("", false)
                        }

                        else -> {
                            activeTextValue.set(currentValue.trim(), true)
                        }
                    }

                    clearActiveTextInput()
                    return
                }

                Keyboard.KEY_ESCAPE -> {
                    clearActiveTextInput()
                    return
                }

                else -> {
                    activeTextField.textboxKeyTyped(typedChar, keyCode)

                    if (!specialAutoTextInput) {
                        activeTextValue.set(activeTextField.getText())
                    }

                    return
                }
            }
        }

        if (activeTextValue != null) {
            when {
                keyCode == Keyboard.KEY_RETURN -> {
                    // When Enter is pressed, we want to trigger the friend addition
                    // if it's the AddFriend TextValue from the Friends module
                    val currentValue = activeTextValue.get()
                    if (currentValue.isNotEmpty() && currentValue.isNotBlank() && activeTextValue.name == "AddFriend") {
                        // Add the friend directly through the Friends module
                        Friends.addFriend(currentValue.trim())

                        // Clear the input field after adding the friend
                        activeTextValue.set("", false) // Don't save the empty value immediately
                    } else if (currentValue.isNotEmpty() && currentValue.isNotBlank() && activeTextValue.name == "AddMessage") {
                        // Add the message directly through the AutoText module
                        net.asd.union.features.module.modules.other.AutoText.addMessage(currentValue.trim(), false)

                        // Clear the input field after adding the message
                        activeTextValue.set("", false) // Don't save the empty value immediately
                        saveConfig(valuesConfig)
                    } else if (activeTextValue.name != "AddFriend" && activeTextValue.name != "AddMessage") {
                        // For other TextValues, just save the value normally
                        activeTextValue.set(currentValue.trim(), true) // Force save immediately
                    }

                    style.activeTextValue = null
                    return
                }
                keyCode == Keyboard.KEY_ESCAPE -> {
                    // Cancel without saving and reset focus
                    style.activeTextValue = null
                    return
                }
                keyCode == Keyboard.KEY_BACK -> {
                    // Remove last character
                    if (activeTextValue.get().isNotEmpty()) {
                        activeTextValue.set(activeTextValue.get().dropLast(1))
                    }
                    return
                }
                typedChar >= ' ' && typedChar <= '~' -> {
                    // Add character to the value
                    activeTextValue.set(activeTextValue.get() + typedChar)
                    return
                }
            }
        }

        // Close ClickGUI by using its key bind.
        if (keyCode == ClickGUIModule.keyBind) {
            if (ignoreClosing) ignoreClosing = false
            else mc.displayGuiScreen(null)

            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        clearActiveTextInput()
        saveConfig(valuesConfig)
        saveConfig(clickGuiConfig)
        for (panel in panels) panel.fade = 0
    }

    override fun initGui() {
        ignoreClosing = true
    }

    private fun clearActiveTextInput() {
        style.activeTextValue = null
        style.activeTextField = null
        AutoText.cancelMessageEdit()
    }

    fun Int.clamp(min: Int, max: Int): Int = this.coerceIn(min, max.coerceAtLeast(0))

    override fun doesGuiPauseGame() = false
}
