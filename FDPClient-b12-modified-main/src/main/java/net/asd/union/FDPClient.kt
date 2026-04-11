/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union

import com.formdev.flatlaf.themes.FlatMacLightLaf
import kotlinx.coroutines.launch
import net.asd.union.event.ClientShutdownEvent
import net.asd.union.event.EventManager
import net.asd.union.event.StartupEvent
import net.asd.union.features.command.CommandManager
import net.asd.union.features.command.CommandManager.registerCommands
import net.asd.union.features.module.ModuleManager
import net.asd.union.features.module.ModuleManager.registerModules
import net.asd.union.file.FileManager
import net.asd.union.file.FileManager.loadAllConfigs
import net.asd.union.file.FileManager.saveAllConfigs
import net.asd.union.handler.api.ClientUpdate
import net.asd.union.handler.api.ClientUpdate.gitInfo
import net.asd.union.handler.api.loadSettings
import net.asd.union.handler.api.messageOfTheDay
import net.asd.union.handler.api.reloadMessageOfTheDay
import net.asd.union.handler.combat.CombatManager
import net.asd.union.handler.lang.LanguageManager.loadLanguages
import net.asd.union.handler.macro.MacroManager
import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.handler.other.SessionStorage
import net.asd.union.handler.payload.ClientFixes
import net.asd.union.handler.render.AntiSpawnLag
import net.asd.union.handler.tabs.BlocksTab
import net.asd.union.handler.tabs.ExploitsTab
import net.asd.union.handler.tabs.HeadsTab
import net.asd.union.script.ScriptManager
import net.asd.union.script.ScriptManager.enableScripts
import net.asd.union.script.ScriptManager.loadScripts
import net.asd.union.script.remapper.Remapper
import net.asd.union.script.remapper.Remapper.loadSrg
import net.asd.union.ui.client.altmanager.GuiAltManager.Companion.loadActiveGenerators
import net.asd.union.ui.client.clickgui.ClickGui
import net.asd.union.ui.font.fontmanager.impl.SimpleFontManager
import net.asd.union.ui.client.gui.GuiClientConfiguration.Companion.updateClientWindow
import net.asd.union.ui.client.hud.HUD
import net.asd.union.ui.client.keybind.KeyBindManager
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.client.ClassUtils.hasForge
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.ClientUtils.disableFastRender
import net.asd.union.utils.client.BlinkUtils
import net.asd.union.utils.client.EntityCache
import net.asd.union.utils.client.ItemCache
import net.asd.union.utils.client.PacketUtils
import net.asd.union.utils.inventory.InventoryManager
import net.asd.union.utils.kotlin.SharedScopes
import net.asd.union.utils.inventory.InventoryUtils
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.io.APIConnectorUtils.performAllChecksAsync
import net.asd.union.utils.movement.BPSUtils
import net.asd.union.utils.movement.MovementUtils
import net.asd.union.utils.movement.TimerBalanceUtils
import net.asd.union.utils.render.MiniMapRegister
import net.asd.union.utils.render.shader.Background
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.timing.TickedActions
import net.asd.union.utils.timing.WaitMsUtils
import net.asd.union.utils.timing.WaitTickUtils
import javax.swing.UIManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

object FDPClient {

    /**
     * Client Information
     *
     * This has all of the basic information.
     */
    const val CLIENT_NAME = "AsdUnionClient"
    const val CLIENT_AUTHOR = "Asd1281yss"
    const val CLIENT_CLOUD = "https://cloud.liquidbounce.net/LiquidBounce"
    const val CLIENT_WEBSITE = "fdpinfo.github.io"
    const val CLIENT_GITHUB = "https://github.com/Itamio/FDPClient"
    const val CLIENT_VERSION = "1.0"
    
    val clientVersionText = gitInfo["git.build.version"]?.toString() ?: "unknown"
    val clientVersionNumber = clientVersionText.substring(1).toIntOrNull() ?: 0 // version format: "b<VERSION>" on legacy
    val clientCommit = gitInfo["git.commit.id.abbrev"]?.let { "git-$it" } ?: "unknown"
    val clientBranch = gitInfo["git.branch"]?.toString() ?: "unknown"

    /**
     * Defines if the client is in development mode.
     * This will enable update checking on commit time instead of regular legacy versioning.
     */
    const val IN_DEV = false

    val clientTitle = "AsdUnionClient - FDPClient Rebranded"

    var isStarting = true
    var isLoadingConfig = true

    // Managers
    val moduleManager = ModuleManager
    val commandManager = CommandManager
    val eventManager = EventManager
    val fileManager = FileManager
    val scriptManager = ScriptManager
    var customFontManager = SimpleFontManager.create()
    val keyBindManager = KeyBindManager

    // HUD & ClickGUI
    var hud = HUD

    val clickGui = ClickGui

    // Menu Background
    var background: Background? = null

    /**
     * Start IO tasks
     */
    fun preload(): Future<*> {
        // Initialize fast startup optimizations
        net.asd.union.utils.performance.FastStartupManager.initializeFastStartup()
        
        // Change theme of Swing
        UIManager.setLookAndFeel(FlatMacLightLaf())

        // Use optimized preload
        return net.asd.union.utils.performance.FastStartupManager.executeOptimizedPreload().thenApply { Unit }
    }

    /**
     * Execute if client will be started
     */
    fun startClient() {
        isStarting = true
        isLoadingConfig = true

        LOGGER.info("Launching...")

        try {
            // Use optimized startup
            net.asd.union.utils.performance.FastStartupManager.executeOptimizedStartup()

            // Register listeners
            RotationUtils
            ClientFixes
            ConnectToRouter
            AntiSpawnLag
            CombatManager
            MacroManager
            SessionStorage
            EntityCache
            ItemCache
            InventoryUtils
            InventoryManager
            MiniMapRegister
            TickedActions
            MovementUtils
            PacketUtils
            TimerBalanceUtils
            BPSUtils
            WaitTickUtils
            SilentHotbar
            WaitMsUtils
            BlinkUtils
            KeyBindManager


            // Register commands
            registerCommands()

            // Note: Module registration is now handled by FastStartupManager
            runCatching {
                // Remapper
                loadSrg()

                if (!Remapper.mappingsLoaded) {
                    error("Failed to load SRG mappings.")
                }

                // ScriptManager
                loadScripts()
                enableScripts()
            }.onFailure {
                LOGGER.error("Failed to load scripts.", it)
            }

            // Load configs
            loadAllConfigs()

            // Update client window
            updateClientWindow()

            // Tabs (Only for Forge!)
            if (hasForge()) {
                BlocksTab()
                ExploitsTab()
            }

            // Disable optifine fastrender
            disableFastRender()

            // Load message of the day
            messageOfTheDay?.message?.let { LOGGER.info("Message of the day: $it") }

            customFontManager = SimpleFontManager.create()

            // Load background
            FileManager.loadBackground()
        } catch (e: Exception) {
            LOGGER.error("Failed to start client: ${e.message}")
        } finally {
            // Set is starting status
            isStarting = false

            // Complete fast startup and show statistics
            net.asd.union.utils.performance.FastStartupManager.completeStartup()

            EventManager.call(StartupEvent)
            LOGGER.info("Successfully started client")
        }
    }

    /**
     * Execute if client will be stopped
     */
    fun stopClient() {
        // Call client shutdown
        EventManager.call(ClientShutdownEvent)

        // Shutdown optimization systems
        net.asd.union.utils.performance.FastStartupManager.shutdown()

        // Stop all CoroutineScopes
        SharedScopes.stop()

        // Save all available configs
        saveAllConfigs()
    }

}
