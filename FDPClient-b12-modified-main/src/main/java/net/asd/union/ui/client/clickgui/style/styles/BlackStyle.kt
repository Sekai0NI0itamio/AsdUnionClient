/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.client.clickgui.style.styles

import net.asd.union.config.*
import net.asd.union.features.module.modules.client.ClickGUIModule.scale
import net.asd.union.features.module.modules.client.Friends
import net.asd.union.features.module.modules.other.AutoText
import net.asd.union.ui.client.clickgui.ClickGui.clamp
import net.asd.union.ui.client.clickgui.Panel
import net.asd.union.ui.client.clickgui.elements.ButtonElement
import net.asd.union.ui.client.clickgui.elements.ModuleElement
import net.asd.union.ui.client.clickgui.style.Style
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.ui.font.Fonts.font35
import net.asd.union.utils.block.BlockUtils.getBlockName
import net.asd.union.utils.extensions.component1
import net.asd.union.utils.extensions.component2
import net.asd.union.utils.extensions.lerpWith
import net.asd.union.utils.render.ColorUtils
import net.asd.union.utils.render.ColorUtils.blendColors
import net.asd.union.utils.render.ColorUtils.withAlpha
import net.asd.union.utils.render.RenderUtils
import net.asd.union.utils.render.RenderUtils.drawBorderedRect
import net.asd.union.utils.render.RenderUtils.drawFilledCircle
import net.asd.union.utils.render.RenderUtils.drawRect
import net.asd.union.utils.render.RenderUtils.drawTexture
import net.asd.union.utils.render.RenderUtils.updateTextureCache
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.StringUtils
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SideOnly(Side.CLIENT)
object BlackStyle : Style() {
    private const val LIST_ENTRY_HEIGHT = 12
    private const val LIST_BOTTOM_PADDING = 4
    private const val LIST_MAX_VISIBLE_ENTRIES = 6
    private const val LIST_VIEWPORT_HEIGHT = LIST_MAX_VISIBLE_ENTRIES * LIST_ENTRY_HEIGHT + LIST_BOTTOM_PADDING
    private const val LIST_SCROLLBAR_WIDTH = 3

    private fun beginTextEdit(value: Value<String>, x: Int, y: Int, width: Int, initialText: String = value.get()): GuiTextField {
        val textField = GuiTextField(value.name.hashCode(), font35, x, y, width, 12).apply {
            setMaxStringLength(32767)
            setFocused(true)
            setCanLoseFocus(false)
            setTextColor(Color.WHITE.rgb)
            setText(initialText)
            setCursorPosition(initialText.length)
            setSelectionPos(initialText.length)
        }

        activeTextValue = value
        activeTextField = textField

        return textField
    }

    private fun ensureTextEdit(value: Value<String>, x: Int, y: Int, width: Int, initialText: String? = null): GuiTextField {
        if (activeTextValue != value || activeTextField == null || initialText != null) {
            return beginTextEdit(value, x, y, width, initialText ?: value.get())
        }

        return activeTextField!!.apply {
            xPosition = x
            yPosition = y
            this.width = width
            height = 12
            setFocused(true)
            setCanLoseFocus(false)
            setTextColor(Color.WHITE.rgb)
        }
    }

    private fun focusTextEditAt(value: Value<String>, x: Int, y: Int, width: Int, clickX: Int, initialText: String? = null): GuiTextField {
        val textField = ensureTextEdit(value, x, y, width, initialText)

        if (width > 0) {
            textField.mouseClicked(clickX.coerceIn(x, x + width), y + textField.height / 2, 0)
        }

        return textField
    }

    private data class ScrollRegion(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
        fun contains(mouseX: Int, mouseY: Int): Boolean = mouseX in left..right && mouseY in top..bottom
        fun isValid(): Boolean = right > left && bottom > top
    }

    private var friendListScroll = 0
    private var messageListScroll = 0
    private val friendListRegion = ScrollRegion()
    private val messageListRegion = ScrollRegion()

    override fun drawPanel(mouseX: Int, mouseY: Int, panel: Panel) {
        drawBorderedRect(
            panel.x, panel.y - 3, panel.x + panel.width, panel.y + 17, 3, Color(20, 20, 20).rgb, Color(20, 20, 20).rgb
        )

        if (panel.fade > 0) {
            drawBorderedRect(
                panel.x,
                panel.y + 17,
                panel.x + panel.width,
                panel.y + 19 + panel.fade,
                3,
                Color(40, 40, 40).rgb,
                Color(40, 40, 40).rgb
            )
            drawBorderedRect(
                panel.x,
                panel.y + 17 + panel.fade,
                panel.x + panel.width,
                panel.y + 24 + panel.fade,
                3,
                Color(20, 20, 20).rgb,
                Color(20, 20, 20).rgb
            )
        }

        val xPos = panel.x - (font35.getStringWidth("§f" + StringUtils.stripControlCodes(panel.name)) - 100) / 2
        font35.drawString(panel.name, xPos, panel.y + 4, Color.WHITE.rgb)
    }

    override fun drawHoverText(mouseX: Int, mouseY: Int, text: String) {
        val lines = text.lines()

        val width =
            lines.maxOfOrNull { font35.getStringWidth(it) + 14 } ?: return // Makes no sense to render empty lines
        val height = (font35.fontHeight * lines.size) + 3

        // Don't draw hover text beyond window boundaries
        val (scaledWidth, scaledHeight) = ScaledResolution(mc)
        val x = mouseX.clamp(0, (scaledWidth / scale - width).roundToInt())
        val y = mouseY.clamp(0, (scaledHeight / scale - height).roundToInt())

        drawBorderedRect(x + 9, y, x + width, y + height, 3, Color(40, 40, 40).rgb, Color(40, 40, 40).rgb)

        lines.forEachIndexed { index, text ->
            font35.drawString(text, x + 12, y + 3 + (font35.fontHeight) * index, Color.WHITE.rgb)
        }
    }

    override fun drawButtonElement(mouseX: Int, mouseY: Int, buttonElement: ButtonElement) {
        drawRect(
            buttonElement.x - 1,
            buttonElement.y - 1,
            buttonElement.x + buttonElement.width + 1,
            buttonElement.y + buttonElement.height + 1,
            getHoverColor(
                if (buttonElement.color != Int.MAX_VALUE) Color(20, 20, 20) else Color(40, 40, 40),
                buttonElement.hoverTime
            )
        )

        font35.drawString(buttonElement.displayName, buttonElement.x + 5, buttonElement.y + 5, Color.WHITE.rgb)
    }

    override fun drawModuleElementAndClick(
        mouseX: Int,
        mouseY: Int,
        moduleElement: ModuleElement,
        mouseButton: Int?
    ): Boolean {
        drawRect(
            moduleElement.x - 1,
            moduleElement.y - 1,
            moduleElement.x + moduleElement.width + 1,
            moduleElement.y + moduleElement.height + 1,
            getHoverColor(Color(40, 40, 40), moduleElement.hoverTime)
        )
        drawRect(
            moduleElement.x - 1,
            moduleElement.y - 1,
            moduleElement.x + moduleElement.width + 1,
            moduleElement.y + moduleElement.height + 1,
            getHoverColor(
                Color(20, 20, 20, moduleElement.slowlyFade),
                moduleElement.hoverTime,
                !moduleElement.module.isActive
            )
        )

        font35.drawString(
            moduleElement.displayName, moduleElement.x + 5, moduleElement.y + 5,
            if (moduleElement.module.state && !moduleElement.module.isActive) Color(255, 255, 255, 128).rgb
            else Color.WHITE.rgb
        )

        // Draw settings
        val moduleValues = moduleElement.module.values.filter { it.shouldRender() }
        if (moduleValues.isNotEmpty()) {
            font35.drawString(
                if (moduleElement.showSettings) "<" else ">",
                moduleElement.x + moduleElement.width - 8,
                moduleElement.y + 5,
                Color.WHITE.rgb
            )

            if (moduleElement.showSettings) {
                var yPos = moduleElement.y + 6

                val minX = moduleElement.x + moduleElement.width + 4
                val maxX = moduleElement.x + moduleElement.width + moduleElement.settingsWidth

                if (moduleElement.settingsWidth > 0 && moduleElement.settingsHeight > 0) drawBorderedRect(
                    minX,
                    yPos,
                    maxX,
                    yPos + moduleElement.settingsHeight,
                    3,
                    Color(20, 20, 20).rgb,
                    Color(40, 40, 40).rgb
                )

                for (value in moduleValues) {
                    assumeNonVolatile = value.get() is Number

                    val suffix = value.suffix ?: ""

                    when (value) {
                        is BoolValue -> {
                            val text = value.name

                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos..yPos + 12) {
                                value.toggle()
                                clickSound()
                                return true
                            }

                            font35.drawString(
                                text, minX + 2, yPos + 2, if (value.get()) Color.WHITE.rgb else Int.MAX_VALUE
                            )

                            yPos += 11
                        }

                        is ListValue -> {
                            val text = value.name

                            moduleElement.settingsWidth = font35.getStringWidth(text) + 16

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos..yPos + font35.fontHeight) {
                                value.openList = !value.openList
                                clickSound()
                                return true
                            }

                            font35.drawString(text, minX + 2, yPos + 2, Color.WHITE.rgb)
                            font35.drawString(
                                if (value.openList) "-" else "+",
                                (maxX - if (value.openList) 5 else 6),
                                yPos + 2,
                                Color.WHITE.rgb
                            )

                            yPos += font35.fontHeight + 1

                            for (valueOfList in value.values) {
                                moduleElement.settingsWidth = font35.getStringWidth("> $valueOfList") + 12

                                if (value.openList) {
                                    if (mouseButton == 0 && mouseX in minX..maxX && mouseY in yPos..yPos + 9) {
                                        value.set(valueOfList)
                                        clickSound()
                                        return true
                                    }

                                    font35.drawString(
                                        "> $valueOfList",
                                        minX + 2,
                                        yPos + 2,
                                        if (value.get() == valueOfList) Color.WHITE.rgb else Int.MAX_VALUE
                                    )

                                    yPos += font35.fontHeight + 1
                                }
                            }
                            if (!value.openList) {
                                yPos += 1
                            }
                        }

                        is FloatValue -> {
                            val text = value.name + "§f: " + round(value.get()) + " §7$suffix"

                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            val x = minX + 4
                            val y = yPos + 14
                            val width = moduleElement.settingsWidth - 12
                            val color = Color(20, 20, 20)

                            val displayValue = value.get().coerceIn(value.range)
                            val sliderValue = (x + width * (displayValue - value.minimum) / (value.maximum - value.minimum)).roundToInt()

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in y - 2..y + 5 || sliderValueHeld == value) {
                                val percentage = (mouseX - x) / width.toFloat()
                                value.setAndSaveValueOnButtonRelease(
                                    round(value.minimum + (value.maximum - value.minimum) * percentage).coerceIn(
                                        value.range
                                    )
                                )

                                sliderValueHeld = value

                                if (mouseButton == 0) return true
                            }

                            drawRect(x, y, x + width, y + 2, Int.MAX_VALUE)
                            drawRect(x, y, sliderValue, y + 2, color.rgb)
                            drawFilledCircle(sliderValue, y + 1, 3f, color)

                            font35.drawString(text, minX + 2, yPos + 3, Color.WHITE.rgb)

                            yPos += 19
                        }

                        is IntegerValue -> {
                            val text = value.name + "§f: " + if (value is BlockValue) {
                                getBlockName(value.get()) + " (" + value.get() + ")"
                            } else {
                                value.get()
                            } + " §7$suffix"

                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            val x = minX + 4
                            val y = yPos + 14
                            val width = moduleElement.settingsWidth - 12
                            val color = Color(20, 20, 20)

                            val displayValue = value.get().coerceIn(value.range)
                            val sliderValue =
                                x + width * (displayValue - value.minimum) / (value.maximum - value.minimum)

                            if (mouseButton == 0 && mouseX in minX..maxX && mouseY in y - 2..y + 5 || sliderValueHeld == value) {
                                val percentage = (mouseX - x) / width.toFloat()
                                value.setAndSaveValueOnButtonRelease(value.lerpWith(percentage).coerceIn(value.range))

                                sliderValueHeld = value

                                if (mouseButton == 0) return true
                            }

                            drawRect(x, y, x + width, y + 2, Int.MAX_VALUE)
                            drawRect(x, y, sliderValue, y + 2, color.rgb)
                            drawFilledCircle(sliderValue, y + 1, 3f, color)

                            font35.drawString(text, minX + 2, yPos + 3, Color.WHITE.rgb)

                            yPos += 19
                        }

                        is IntegerRangeValue -> {
                            val slider1 = value.get().first
                            val slider2 = value.get().last

                            val text = "${value.name}§f: $slider1 - $slider2 §7$suffix§f (Beta)"
                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            val x = minX + 4
                            val y = yPos + 14
                            val width = moduleElement.settingsWidth - 12
                            val color = Color(20, 20, 20)

                            if (mouseButton == 0 && mouseX in x..x + width && mouseY in y - 2..y + 5 || sliderValueHeld == value) {
                                val slider1Pos =
                                    minX + ((slider1 - value.minimum).toFloat() / (value.maximum - value.minimum)) * (maxX - minX)
                                val slider2Pos =
                                    minX + ((slider2 - value.minimum).toFloat() / (value.maximum - value.minimum)) * (maxX - minX)

                                val distToSlider1 = mouseX - slider1Pos
                                val distToSlider2 = mouseX - slider2Pos

                                val percentage = (mouseX - minX - 4F) / (maxX - minX - 8F)

                                if (abs(distToSlider1) <= abs(distToSlider2) && distToSlider2 <= 0) {
                                    withDelayedSave {
                                        value.setFirst(value.lerpWith(percentage).coerceIn(value.minimum, slider2), false)
                                    }
                                } else {
                                    withDelayedSave {
                                        value.setLast(value.lerpWith(percentage).coerceIn(slider1, value.maximum), false)
                                    }
                                }

                                sliderValueHeld = value

                                if (mouseButton == 0) return true
                            }

                            val displayValue1 = value.get().first
                            val displayValue2 = value.get().last

                            val sliderValue1 =
                                x + width * (displayValue1 - value.minimum) / (value.maximum - value.minimum)
                            val sliderValue2 =
                                x + width * (displayValue2 - value.minimum) / (value.maximum - value.minimum)

                            drawRect(x, y, x + width, y + 2, Int.MAX_VALUE)
                            drawRect(sliderValue1, y, sliderValue2, y + 2, color.rgb)
                            drawFilledCircle(sliderValue1, y + 1, 3f, color)
                            drawFilledCircle(sliderValue2, y + 1, 3f, color)

                            font35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 19
                        }

                        is FloatRangeValue -> {
                            val slider1 = value.get().start
                            val slider2 = value.get().endInclusive

                            val text = "${value.name}§f: ${round(slider1)} - ${round(slider2)} §7$suffix§f (Beta)"
                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            val x = minX + 4
                            val y = yPos + 14
                            val width = moduleElement.settingsWidth - 12
                            val color = Color(20, 20, 20)

                            if (mouseButton == 0 && mouseX in x..x + width && mouseY in y - 2..y + 5 || sliderValueHeld == value) {
                                val slider1Pos =
                                    minX + ((slider1 - value.minimum) / (value.maximum - value.minimum)) * (maxX - minX)
                                val slider2Pos =
                                    minX + ((slider2 - value.minimum) / (value.maximum - value.minimum)) * (maxX - minX)

                                val distToSlider1 = mouseX - slider1Pos
                                val distToSlider2 = mouseX - slider2Pos

                                val percentage = (mouseX - minX - 4F) / (maxX - minX - 8F)

                                if (abs(distToSlider1) <= abs(distToSlider2) && distToSlider2 <= 0) {
                                    withDelayedSave {
                                        value.setFirst(value.lerpWith(percentage).coerceIn(value.minimum, slider2), false)
                                    }
                                } else {
                                    withDelayedSave {
                                        value.setLast(value.lerpWith(percentage).coerceIn(slider1, value.maximum), false)
                                    }
                                }

                                sliderValueHeld = value

                                if (mouseButton == 0) return true
                            }

                            val displayValue1 = value.get().start
                            val displayValue2 = value.get().endInclusive

                            val sliderValue1 =
                                x + width * (displayValue1 - value.minimum) / (value.maximum - value.minimum)
                            val sliderValue2 =
                                x + width * (displayValue2 - value.minimum) / (value.maximum - value.minimum)

                            drawRect(x, y, x + width, y + 2, Int.MAX_VALUE)
                            drawRect(sliderValue1, y.toFloat(), sliderValue2, y + 2f, color.rgb)
                            drawFilledCircle(sliderValue1.roundToInt(), y + 1, 3f, color)
                            drawFilledCircle(sliderValue2.roundToInt(), y + 1, 3f, color)

                            font35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 19
                        }

                        is FontValue -> {
                            val displayString = value.displayName
                            moduleElement.settingsWidth = font35.getStringWidth(displayString) + 8

                            font35.drawString(displayString, minX + 2, yPos + 2, Color.WHITE.rgb)

                            if (mouseButton != null && mouseX in minX..maxX && mouseY in yPos..yPos + 12) {
                                if (mouseButton == 0) value.next() else value.previous()
                                clickSound()
                                return true
                            }

                            yPos += 11
                        }

                        is TextValue -> {
                            fun isHovered(x1: Int, x2: Int, y1: Int, y2: Int, mx: Int, my: Int): Boolean {
                                return mx in x1..x2 && my in y1..y2
                            }

                            val editorWidth = (maxX - minX).coerceAtMost(200).coerceAtLeast(0)
                            val inputY = yPos
                            val isFocused = activeTextValue == value && activeTextField != null

                            if (isFocused) {
                                moduleElement.settingsWidth = editorWidth

                                val textField = ensureTextEdit(value, minX, inputY, editorWidth)

                                font35.drawString(value.name + ":", minX + 2, inputY - 9, Color(167, 167, 167).rgb)
                                textField.drawTextBox()

                                if (mouseButton == 0) {
                                    if (isHovered(minX, minX + editorWidth, inputY, inputY + 12, mouseX, mouseY)) {
                                        textField.mouseClicked(mouseX, mouseY, mouseButton)
                                        return true
                                    }

                                    activeTextValue = null
                                    activeTextField = null
                                    AutoText.cancelMessageEdit()
                                }
                            } else {
                                val displayText = value.name + ": " + (if (value.get().isEmpty()) "<click to enter>" else value.get())
                                moduleElement.settingsWidth = font35.getStringWidth(displayText) + 8

                                font35.drawString(
                                    displayText,
                                    minX + 2,
                                    inputY + 2,
                                    if (isHovered(minX, maxX, inputY, inputY + 10, mouseX, mouseY)) Color(167, 167, 167).rgb else Color.WHITE.rgb
                                )

                                if (mouseButton == 0 && isHovered(minX, maxX, inputY, inputY + 10, mouseX, mouseY)) {
                                    focusTextEditAt(value, minX, inputY, editorWidth, mouseX)
                                    AutoText.cancelMessageEdit()
                                    return true
                                }
                            }

                            yPos += 11

                            if (value.name == "AddFriend") {
                                val friends = Friends.getFriends()
                                val listTop = yPos
                                val listHeight = LIST_VIEWPORT_HEIGHT
                                val listBottom = listTop + listHeight

                                friendListScroll = friendListScroll.coerceIn(0, max(0, friends.size - LIST_MAX_VISIBLE_ENTRIES))
                                friendListRegion.left = minX
                                friendListRegion.top = listTop
                                friendListRegion.right = maxX
                                friendListRegion.bottom = listBottom

                                if (listHeight > 0) {
                                    drawRect(minX, listTop, maxX, listBottom, Color(28, 28, 28, 180).rgb)

                                    val startIndex = friendListScroll
                                    val endIndex = min(friends.size, startIndex + LIST_MAX_VISIBLE_ENTRIES)

                                    for (index in startIndex until endIndex) {
                                        val friend = friends[index]
                                        val friendY = listTop + (index - startIndex) * LIST_ENTRY_HEIGHT
                                        val removeButtonX = maxX - 15

                                        font35.drawString(friend, minX + 4, friendY + 2, Color.WHITE.rgb)
                                        drawRect(removeButtonX - 10, friendY, removeButtonX, friendY + 10, Color(200, 50, 50).rgb)
                                        font35.drawString("X", removeButtonX - 7, friendY + 2, Color.WHITE.rgb)

                                        if (mouseButton == 0 && isHovered(removeButtonX - 10, removeButtonX, friendY, friendY + 10, mouseX, mouseY)) {
                                            Friends.removeFriend(friend)
                                            return true
                                        }
                                    }

                                    if (friends.size > LIST_MAX_VISIBLE_ENTRIES) {
                                        val trackX1 = maxX - LIST_SCROLLBAR_WIDTH
                                        val trackX2 = maxX
                                        val trackHeight = listHeight
                                        val maxScroll = friends.size - LIST_MAX_VISIBLE_ENTRIES
                                        val thumbHeight = max(
                                            10,
                                            (trackHeight.toFloat() * LIST_MAX_VISIBLE_ENTRIES / friends.size).roundToInt()
                                        )
                                        val thumbTravel = max(1, trackHeight - thumbHeight)
                                        val thumbTop = listTop + ((friendListScroll.toFloat() / maxScroll) * thumbTravel).roundToInt()

                                        drawRect(trackX1, listTop, trackX2, listBottom, Color(15, 15, 15, 160).rgb)
                                        drawRect(trackX1, thumbTop, trackX2, thumbTop + thumbHeight, Color(95, 95, 95, 220).rgb)
                                    }

                                    yPos += listHeight
                                }

                                if (listHeight > 0) yPos += LIST_BOTTOM_PADDING
                            }

                            if (value.name == "AddMessage") {
                                val messages = AutoText.getMessages()
                                val listTop = yPos
                                val listHeight = LIST_VIEWPORT_HEIGHT
                                val listBottom = listTop + listHeight

                                messageListScroll = messageListScroll.coerceIn(0, max(0, messages.size - LIST_MAX_VISIBLE_ENTRIES))
                                messageListRegion.left = minX
                                messageListRegion.top = listTop
                                messageListRegion.right = maxX
                                messageListRegion.bottom = listBottom

                                if (listHeight > 0) {
                                    drawRect(minX, listTop, maxX, listBottom, Color(28, 28, 28, 180).rgb)

                                    val startIndex = messageListScroll
                                    val endIndex = min(messages.size, startIndex + LIST_MAX_VISIBLE_ENTRIES)

                                    for (index in startIndex until endIndex) {
                                        val (id, message) = messages[index]
                                        val displayText = "[$id] ${if (message.length > 25) message.take(25) + "..." else message}"
                                        val removeButtonX = maxX - 15
                                        val messageY = listTop + (index - startIndex) * LIST_ENTRY_HEIGHT
                                        val editingMessage = AutoText.getEditingMessageId() == id

                                        if (editingMessage) {
                                            drawRect(minX, messageY, removeButtonX - 10, messageY + 10, Color(60, 90, 120, 120).rgb)
                                        }

                                        font35.drawString(displayText, minX + 4, messageY + 2, if (editingMessage) Color(170, 220, 255).rgb else Color.WHITE.rgb)
                                        drawRect(removeButtonX - 10, messageY, removeButtonX, messageY + 10, Color(200, 50, 50).rgb)
                                        font35.drawString("X", removeButtonX - 7, messageY + 2, Color.WHITE.rgb)

                                        if (mouseButton == 0 && isHovered(removeButtonX - 10, removeButtonX, messageY, messageY + 10, mouseX, mouseY)) {
                                            AutoText.removeMessage(id)
                                            return true
                                        }

                                        if (mouseButton == 0 && isHovered(minX, removeButtonX - 11, messageY, messageY + 10, mouseX, mouseY)) {
                                            val editedMessage = AutoText.beginMessageEdit(id) ?: return true
                                            focusTextEditAt(value, minX, inputY, editorWidth, mouseX, editedMessage)
                                            return true
                                        }
                                    }

                                    if (messages.size > LIST_MAX_VISIBLE_ENTRIES) {
                                        val trackX1 = maxX - LIST_SCROLLBAR_WIDTH
                                        val trackX2 = maxX
                                        val trackHeight = listHeight
                                        val maxScroll = messages.size - LIST_MAX_VISIBLE_ENTRIES
                                        val thumbHeight = max(
                                            10,
                                            (trackHeight.toFloat() * LIST_MAX_VISIBLE_ENTRIES / messages.size).roundToInt()
                                        )
                                        val thumbTravel = max(1, trackHeight - thumbHeight)
                                        val thumbTop = listTop + ((messageListScroll.toFloat() / maxScroll) * thumbTravel).roundToInt()

                                        drawRect(trackX1, listTop, trackX2, listBottom, Color(15, 15, 15, 160).rgb)
                                        drawRect(trackX1, thumbTop, trackX2, thumbTop + thumbHeight, Color(95, 95, 95, 220).rgb)
                                    }

                                    yPos += listHeight
                                }

                                if (listHeight > 0) yPos += LIST_BOTTOM_PADDING
                            }
                        }

                        is ColorValue -> {
                            val currentColor = value.selectedColor()

                            val spacing = 12

                            val startX = moduleElement.x + moduleElement.width + 4
                            val startY = yPos - 1

                            // Color preview
                            val colorPreviewSize = 9
                            val colorPreviewX2 = maxX - colorPreviewSize
                            val colorPreviewX1 = colorPreviewX2 - colorPreviewSize
                            val colorPreviewY1 = startY + 1
                            val colorPreviewY2 = colorPreviewY1 + colorPreviewSize

                            val rainbowPreviewX2 = colorPreviewX1 - colorPreviewSize
                            val rainbowPreviewX1 = rainbowPreviewX2 - colorPreviewSize

                            // Text
                            val textX = startX + 2F
                            val textY = startY + 3F

                            // Sliders
                            val hueSliderWidth = 7
                            val hueSliderHeight = 50
                            val colorPickerWidth = 75
                            val colorPickerHeight = 50

                            val spacingBetweenSliders = 5

                            val colorPickerStartX = textX.toInt()
                            val colorPickerEndX = colorPickerStartX + colorPickerWidth
                            val colorPickerStartY = colorPreviewY2 + spacing / 3
                            val colorPickerEndY = colorPickerStartY + colorPickerHeight

                            val hueSliderStartY = colorPickerStartY
                            val hueSliderEndY = colorPickerStartY + hueSliderHeight

                            val hueSliderX = colorPickerEndX + spacingBetweenSliders

                            val opacityStartX = hueSliderX + hueSliderWidth + spacingBetweenSliders
                            val opacityEndX = opacityStartX + hueSliderWidth

                            val rainbow = value.rainbow

                            if (mouseButton in arrayOf(0, 1)) {
                                val isColorPreview = mouseX in colorPreviewX1..colorPreviewX2 && mouseY in colorPreviewY1..colorPreviewY2
                                val isRainbowPreview = mouseX in rainbowPreviewX1..rainbowPreviewX2 && mouseY in colorPreviewY1..colorPreviewY2

                                when {
                                    isColorPreview -> {
                                        if (mouseButton == 0 && rainbow) value.rainbow = false
                                        if (mouseButton == 1) value.showPicker = !value.showPicker
                                        clickSound()
                                        return true
                                    }
                                    isRainbowPreview -> {
                                        if (mouseButton == 0) value.rainbow = true
                                        if (mouseButton == 1) value.showPicker = !value.showPicker
                                        clickSound()
                                        return true
                                    }
                                }
                            }

                            val display = "${value.name}: ${"#%08X".format(currentColor.rgb)}"

                            val combinedWidth = opacityEndX - colorPickerStartX
                            val optimalWidth = maxOf(font35.getStringWidth(display), combinedWidth)

                            moduleElement.settingsWidth = optimalWidth + spacing * 4

                            font35.drawString(display, textX, textY, Color.WHITE.rgb)

                            val normalBorderColor = if (rainbow) 0 else Color.BLUE.rgb
                            val rainbowBorderColor = if (rainbow) Color.BLUE.rgb else 0

                            val hue = if (rainbow) {
                                Color.RGBtoHSB(currentColor.red, currentColor.green, currentColor.blue, null)[0]
                            } else {
                                value.hueSliderY
                            }

                            if (value.showPicker) {
                                // Color Picker
                                value.updateTextureCache(
                                    id = 0,
                                    hue = hue,
                                    width = colorPickerWidth,
                                    height = colorPickerHeight,
                                    generateImage = { image, _ ->
                                        for (px in 0 until colorPickerWidth) {
                                            for (py in 0 until colorPickerHeight) {
                                                val localS = px / colorPickerWidth.toFloat()
                                                val localB = 1.0f - (py / colorPickerHeight.toFloat())
                                                val rgb = Color.HSBtoRGB(hue, localS, localB)
                                                image.setRGB(px, py, rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id,
                                            colorPickerStartX,
                                            colorPickerStartY,
                                            colorPickerWidth,
                                            colorPickerHeight
                                        )
                                    })

                                val markerX = (colorPickerStartX..colorPickerEndX).lerpWith(value.colorPickerPos.x)
                                val markerY = (colorPickerStartY..colorPickerEndY).lerpWith(value.colorPickerPos.y)

                                if (!rainbow) {
                                    RenderUtils.drawBorder(
                                        markerX - 2f, markerY - 2f, markerX + 3f, markerY + 3f, 1.5f, Color.WHITE.rgb
                                    )
                                }

                                // Hue slider
                                value.updateTextureCache(
                                    id = 1,
                                    hue = hue,
                                    width = hueSliderWidth,
                                    height = hueSliderHeight,
                                    generateImage = { image, _ ->
                                        for (y in 0 until hueSliderHeight) {
                                            for (x in 0 until hueSliderWidth) {
                                                val localHue = y / hueSliderHeight.toFloat()
                                                val rgb = Color.HSBtoRGB(localHue, 1.0f, 1.0f)
                                                image.setRGB(x, y, rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id,
                                            hueSliderX,
                                            colorPickerStartY,
                                            hueSliderWidth,
                                            hueSliderHeight
                                        )
                                    })

                                // Opacity slider
                                value.updateTextureCache(
                                    id = 2,
                                    hue = currentColor.rgb.toFloat(),
                                    width = hueSliderWidth,
                                    height = hueSliderHeight,
                                    generateImage = { image, _ ->
                                        val gridSize = 1

                                        for (y in 0 until hueSliderHeight) {
                                            for (x in 0 until hueSliderWidth) {
                                                val gridX = x / gridSize
                                                val gridY = y / gridSize

                                                val checkerboardColor = if ((gridY + gridX) % 2 == 0) {
                                                    Color.WHITE.rgb
                                                } else {
                                                    Color.BLACK.rgb
                                                }

                                                val alpha =
                                                    ((1 - y.toFloat() / hueSliderHeight.toFloat()) * 255).roundToInt()

                                                val finalColor = blendColors(
                                                    Color(checkerboardColor),
                                                    currentColor.withAlpha(alpha)
                                                )

                                                image.setRGB(x, y, finalColor.rgb)
                                            }
                                        }
                                    },
                                    drawAt = { id ->
                                        drawTexture(
                                            id,
                                            opacityStartX,
                                            colorPickerStartY,
                                            hueSliderWidth,
                                            hueSliderHeight
                                        )
                                    })

                                val opacityMarkerY =
                                    (hueSliderStartY..hueSliderEndY).lerpWith(1 - value.opacitySliderY)
                                val hueMarkerY = (hueSliderStartY..hueSliderEndY).lerpWith(hue)

                                RenderUtils.drawBorder(
                                    hueSliderX.toFloat() - 1,
                                    hueMarkerY - 1f,
                                    hueSliderX + hueSliderWidth + 1f,
                                    hueMarkerY + 1f,
                                    1.5f,
                                    Color.WHITE.rgb,
                                )

                                RenderUtils.drawBorder(
                                    opacityStartX.toFloat() - 1,
                                    opacityMarkerY - 1f,
                                    opacityEndX + 1f,
                                    opacityMarkerY + 1f,
                                    1.5f,
                                    Color.WHITE.rgb,
                                )

                                val inColorPicker =
                                    mouseX in colorPickerStartX until colorPickerEndX && mouseY in colorPickerStartY until colorPickerEndY && !rainbow
                                val inHueSlider =
                                    mouseX in hueSliderX - 1..hueSliderX + hueSliderWidth + 1 && mouseY in hueSliderStartY until hueSliderEndY && !rainbow
                                val inOpacitySlider =
                                    mouseX in opacityStartX - 1..opacityEndX + 1 && mouseY in hueSliderStartY until hueSliderEndY

                                // Must be outside the if statements below since we check for mouse button state.
                                // If it's inside the statement, it will not update the mouse button state on time.
                                val sliderType = value.lastChosenSlider

                                if (mouseButton == 0 && (inColorPicker || inHueSlider || inOpacitySlider)
                                    || sliderValueHeld == value && value.lastChosenSlider != null
                                ) {
                                    if (inColorPicker && sliderType == null || sliderType == ColorValue.SliderType.COLOR) {
                                        val newS =
                                            ((mouseX - colorPickerStartX) / colorPickerWidth.toFloat()).coerceIn(
                                                0f,
                                                1f
                                            )
                                        val newB =
                                            (1.0f - (mouseY - colorPickerStartY) / colorPickerHeight.toFloat()).coerceIn(
                                                0f,
                                                1f
                                            )
                                        value.colorPickerPos.x = newS
                                        value.colorPickerPos.y = 1 - newB
                                    }

                                    var finalColor = Color(
                                        Color.HSBtoRGB(
                                            value.hueSliderY,
                                            value.colorPickerPos.x,
                                            1 - value.colorPickerPos.y
                                        )
                                    )

                                    if (inHueSlider && sliderType == null || sliderType == ColorValue.SliderType.HUE) {
                                        value.hueSliderY =
                                            ((mouseY - hueSliderStartY) / hueSliderHeight.toFloat()).coerceIn(
                                                0f,
                                                1f
                                            )

                                        finalColor = Color(
                                            Color.HSBtoRGB(
                                                value.hueSliderY,
                                                value.colorPickerPos.x,
                                                1 - value.colorPickerPos.y
                                            )
                                        )
                                    }

                                    if (inOpacitySlider && sliderType == null || sliderType == ColorValue.SliderType.OPACITY) {
                                        value.opacitySliderY =
                                            1 - ((mouseY - hueSliderStartY) / hueSliderHeight.toFloat()).coerceIn(
                                                0f,
                                                1f
                                            )
                                    }

                                    finalColor =
                                        finalColor.withAlpha((value.opacitySliderY * 255).roundToInt())

                                    sliderValueHeld = value

                                    value.setAndSaveValueOnButtonRelease(finalColor)

                                    if (mouseButton == 0) {
                                        value.lastChosenSlider = when {
                                            inColorPicker && !rainbow -> ColorValue.SliderType.COLOR
                                            inHueSlider && !rainbow -> ColorValue.SliderType.HUE
                                            inOpacitySlider -> ColorValue.SliderType.OPACITY
                                            else -> null
                                        }
                                        return true
                                    }
                                }
                                yPos += colorPickerHeight + colorPreviewSize - 6
                            }
                            drawBorderedRect(
                                colorPreviewX1,
                                colorPreviewY1,
                                colorPreviewX2,
                                colorPreviewY2,
                                1.5,
                                normalBorderColor,
                                value.get().rgb
                            )

                            drawBorderedRect(
                                rainbowPreviewX1,
                                colorPreviewY1,
                                rainbowPreviewX2,
                                colorPreviewY2,
                                1.5f,
                                rainbowBorderColor,
                                ColorUtils.rainbow(alpha = value.opacitySliderY).rgb
                            )

                            yPos += spacing
                        }

                        else -> {
                            val text = value.name + "§f: " + value.get()

                            moduleElement.settingsWidth = font35.getStringWidth(text) + 8

                            font35.drawString(text, minX + 2, yPos + 4, Color.WHITE.rgb)

                            yPos += 12
                        }
                    }
                }

                moduleElement.settingsHeight = yPos - moduleElement.y - 6

                if (mouseButton != null && mouseX in minX..maxX && mouseY in moduleElement.y + 6..yPos + 2) return true
            }
        }

        if (mouseButton == -1) {
            sliderValueHeld = null
        }

        return false
    }

    override fun handleScroll(mouseX: Int, mouseY: Int, wheel: Int): Boolean {
        if (wheel == 0) {
            return false
        }

        if (friendListRegion.isValid() && friendListRegion.contains(mouseX, mouseY)) {
            if (wheel < 0) friendListScroll++ else friendListScroll--
            return true
        }

        if (messageListRegion.isValid() && messageListRegion.contains(mouseX, mouseY)) {
            if (wheel < 0) messageListScroll++ else messageListScroll--
            return true
        }

        return false
    }
}
