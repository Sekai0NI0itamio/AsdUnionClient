/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.ui.client.gui

import net.asd.union.FDPClient.CLIENT_NAME
import net.asd.union.FDPClient.clientVersionText
import net.asd.union.event.EventManager
import net.asd.union.event.SessionUpdateEvent
import net.asd.union.features.module.modules.client.HUDModule.guiColor
import net.asd.union.handler.sessiontabs.ClientTabManager
import net.asd.union.handler.other.SessionStorage
import net.asd.union.ui.client.altmanager.GuiAltManager
import net.asd.union.ui.client.clickgui.ClickGui
import net.asd.union.ui.client.gui.button.ImageButton
import net.asd.union.ui.client.gui.button.QuitButton
import net.asd.union.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.asd.union.ui.font.Fonts
import net.asd.union.ui.font.Fonts.minecraftFont
import net.asd.union.utils.io.GitUtils
import net.asd.union.utils.kotlin.RandomUtils
import net.asd.union.utils.render.RenderUtils.drawBloom
import net.asd.union.utils.render.RenderUtils.drawShadowRect
import net.asd.union.utils.ui.AbstractScreen
import net.minecraft.client.gui.*
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import net.minecraft.util.Session
import net.minecraftforge.fml.client.GuiModList
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.util.*

class GuiMainMenu : AbstractScreen(), GuiYesNoCallback {

    private var logo: ResourceLocation? = null

    private lateinit var btnSinglePlayer: GuiButton
    private lateinit var btnMultiplayer: GuiButton
    private lateinit var btnClientOptions: GuiButton

    private lateinit var btnClickGUI: ImageButton
    private lateinit var btnCommitInfo: ImageButton
    private lateinit var btnCosmetics: ImageButton
    private lateinit var btnMinecraftOptions: ImageButton
    private lateinit var btnLanguage: ImageButton
    private lateinit var btnForgeModList: ImageButton
    private lateinit var btnAddAccount: ImageButton

    private lateinit var btnQuit: QuitButton
    private lateinit var accountTextField: GuiTextField
    private lateinit var btnRandomAccount: GuiButton
    private lateinit var btnTabBarToggle: GuiButton

    override fun initGui() {
        buttonList.clear()
        logo = ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/logo.png")
        SessionStorage.applySavedUsername()
        val topInset = ClientTabManager.contentTop(this)
        val centerY = height / 2 - 80
        val buttonWidth = 133
        val buttonHeight = 20
        btnSinglePlayer = +GuiButton(
            0,        // ID
            width / 2 - 66,
            centerY + 70,
            buttonWidth, buttonHeight,
            "SINGLE PLAYER"
        )
        btnMultiplayer = +GuiButton(
            1,
            width / 2 - 66,
            centerY + 95 - 2,
            buttonWidth, buttonHeight,
            "MULTI PLAYER"
        )
        btnClientOptions = +GuiButton(
            2,
            width / 2 - 66,
            centerY + 120 - 4,
            buttonWidth, buttonHeight,
            "SETTINGS"
        )

        val bottomY = height - 20
        btnClickGUI = ImageButton("CLICKGUI", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/clickgui.png"), width / 2 - 45, bottomY)
        btnCommitInfo = ImageButton("COMMIT INFO", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/github.png"), width / 2 - 30, bottomY)
        btnCosmetics = ImageButton("COSMETICS", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/cosmetics.png"), width / 2 - 15, bottomY)
        btnMinecraftOptions = ImageButton("MINECRAFT SETTINGS", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/cog.png"), width / 2, bottomY)
        btnLanguage = ImageButton("LANGUAGE", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/globe.png"), width / 2 + 15, bottomY)
        btnForgeModList = ImageButton("FORGE MODS", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/forge.png"), width / 2 + 30, bottomY)

        btnAddAccount = ImageButton("ALT MANAGER", ResourceLocation("${CLIENT_NAME.lowercase()}/mainmenu/add-account.png"), width - 55, topInset + 7)
        btnQuit = QuitButton(width - 17, topInset + 7)
        btnTabBarToggle = GuiButton(101, 10, topInset + 7, 130, 20, tabBarToggleText())
        accountTextField = GuiTextField(10, mc.fontRendererObj, 10, topInset + 35, 150, 20).apply {
            maxStringLength = 32
            text = mc.session.username
        }
        btnRandomAccount = GuiButton(100, 165, topInset + 35, 50, 20, "Rand")

        buttonList.addAll(listOf(btnSinglePlayer, btnMultiplayer, btnClientOptions, btnRandomAccount, btnTabBarToggle))
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        accountTextField.textboxKeyTyped(typedChar, keyCode)

        if (keyCode == Keyboard.KEY_RETURN) {
            val accountName = accountTextField.text.trim()
            if (accountName.isNotEmpty()) {
                setOfflineAccount(accountName)
            }
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        if (ClientTabManager.handleScreenMouseClick(this, mouseX, mouseY, button)) {
            return
        }

        accountTextField.mouseClicked(mouseX, mouseY, button)

        buttonList.forEach { guiButton ->
            if (guiButton.mousePressed(mc, mouseX, mouseY)) {
                actionPerformed(guiButton)
            }
        }

        when {
            btnQuit.hoverFade > 0 -> mc.shutdown()
            btnMinecraftOptions.hoverFade > 0 ->
                mc.displayGuiScreen(GuiOptions(this, mc.gameSettings))
            btnLanguage.hoverFade > 0 ->
                mc.displayGuiScreen(GuiLanguage(this, mc.gameSettings, mc.languageManager))
            btnCommitInfo.hoverFade > 0 ->
                mc.displayGuiScreen(GuiCommitInfo())
            btnForgeModList.hoverFade > 0 ->
                mc.displayGuiScreen(GuiModList(mc.currentScreen))
            btnCosmetics.hoverFade > 0 ->
                mc.displayGuiScreen(GuiCommitInfo())
            btnClickGUI.hoverFade > 0 ->
                mc.displayGuiScreen(ClickGui)
            btnAddAccount.hoverFade > 0 ->
                mc.displayGuiScreen(GuiAltManager(this))
        }
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(GuiSelectWorld(this))
            1 -> mc.displayGuiScreen(GuiMultiplayer(this))
            2 -> mc.displayGuiScreen(GuiInfo(this))
            100 -> {
                val randomName = RandomUtils.randomUsername()
                accountTextField.text = randomName
                setOfflineAccount(randomName)
            }
            101 -> {
                ClientTabManager.toggleTabBarVisible()
                applyTopInsetLayout()
            }
            3 -> {} // Update check removed - do nothing
        }
    }

    private fun setOfflineAccount(username: String) {
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
        mc.session = Session(username, uuid.toString(), "-", "legacy")
        accountTextField.text = username
        EventManager.call(SessionUpdateEvent)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile = true

        applyTopInsetLayout()

        drawBackground(0)

        if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            mc.displayGuiScreen(ClickGui)
        }

        GlStateManager.pushMatrix()

        drawShadowRect(
            (width / 2 - 130).toFloat(),
            (height / 2 - 90).toFloat(),
            (width / 2 + 130).toFloat(),
            (height / 2 + 90).toFloat(),
            15F,
            Color(44, 43, 43, 100).rgb
        )

        GlStateManager.disableAlpha()
        GlStateManager.enableAlpha()
        GlStateManager.enableBlend()
        GlStateManager.color(1.0f, 1.0f, 1.0f)
        mc.textureManager.bindTexture(logo)
        drawModalRectWithCustomSizedTexture(
            width / 2 - 25,
            height / 2 - 68,
            0f,
            0f,
            49,
            49,
            49f,
            49f
        )


        val textClientNameX = width - 4f - minecraftFont.getStringWidth(CLIENT_NAME)
        minecraftFont.drawStringWithShadow(
            CLIENT_NAME,
            textClientNameX,
            height - 23f,
            Color(255, 255, 255, 140).rgb
        )

        val uiMessage = " §e(Stable)"
        val buildInfoText = "Your currently build is $clientVersionText$uiMessage"
        val buildInfoX = width - 4f - minecraftFont.getStringWidth(buildInfoText)
        minecraftFont.drawStringWithShadow(
            buildInfoText,
            buildInfoX,
            height - 12f,
            Color(255, 255, 255, 140).rgb
        )



        Fonts.InterMedium_15.drawCenteredStringShadow(
            "by Asd1281yss",
            width / 2f,
            height / 2f - 19,
            Color(255, 255, 255, 100).rgb
        )

        listOf(btnSinglePlayer, btnMultiplayer, btnClientOptions).forEach {
            it.drawButton(mc, mouseX, mouseY)
        }

        listOf(
            btnClickGUI, btnCommitInfo, btnCosmetics, btnMinecraftOptions,
            btnLanguage, btnForgeModList, btnAddAccount, btnQuit
        ).forEach {
            it.drawButton(mouseX, mouseY)
        }

        val branch = GitUtils.gitBranch
        val commitIdAbbrev = GitUtils.gitInfo.getProperty("git.commit.id.abbrev")
        val infoStr = "$CLIENT_NAME($branch/$commitIdAbbrev) | Minecraft 1.8.9"
        Fonts.font35.drawCenteredStringWithShadow(
            infoStr,
            7F,
            (this.height - 11).toFloat(),
            Color(255, 255, 255, 100).rgb
        )

        drawAccountBarControls(mouseX, mouseY)

        drawBloom(mouseX - 5, mouseY - 5, 10, 10, 16, Color(guiColor))

        GlStateManager.popMatrix()

        assumeNonVolatile = false

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawAccountBarControls(mouseX: Int, mouseY: Int) {
        btnTabBarToggle.drawButton(mc, mouseX, mouseY)

        val topInset = ClientTabManager.contentTop(this)

        minecraftFont.drawStringWithShadow("Account:", 10f, (topInset + 23).toFloat(), Color(255, 255, 255, 200).rgb)
        minecraftFont.drawStringWithShadow(mc.session.username, 70f, (topInset + 23).toFloat(), Color(0, 255, 0, 255).rgb)

        Gui.drawRect(9, topInset + 34, 161, topInset + 56, Color(0, 0, 0, 150).rgb)
        accountTextField.drawTextBox()
        btnRandomAccount.drawButton(mc, mouseX, mouseY)
    }

    override fun updateScreen() {
        accountTextField.updateCursorCounter()
        super.updateScreen()
    }

    private fun applyTopInsetLayout() {
        val topInset = ClientTabManager.contentTop(this)

        if (::btnAddAccount.isInitialized) {
            btnAddAccount.y = topInset + 7
        }
        if (::btnQuit.isInitialized) {
            btnQuit.y = topInset + 7
        }
        if (::btnTabBarToggle.isInitialized) {
            btnTabBarToggle.yPosition = topInset + 7
            btnTabBarToggle.displayString = tabBarToggleText()
        }
        if (::accountTextField.isInitialized) {
            accountTextField.yPosition = topInset + 35
        }
        if (::btnRandomAccount.isInitialized) {
            btnRandomAccount.yPosition = topInset + 35
        }
    }

    private fun tabBarToggleText() = if (ClientTabManager.isTabBarVisible()) {
        "TAB BAR: ON"
    } else {
        "TAB BAR: OFF"
    }
}
