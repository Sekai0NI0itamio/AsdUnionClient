/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.liuli.elixir.account.CrackedAccount
import net.asd.union.config.ListValue
import net.asd.union.config.TextValue
import net.asd.union.config.boolean
import net.asd.union.config.int
import net.asd.union.event.*
import net.asd.union.event.EventManager.call
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.file.FileManager.accountsConfig
import net.asd.union.file.FileManager
import net.asd.union.ui.client.hud.HUD.addNotification
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import net.asd.union.utils.client.ServerUtils
import net.minecraft.client.Minecraft
import net.asd.union.utils.client.chat
import net.asd.union.utils.kotlin.RandomUtils.randomAccount
import net.asd.union.utils.kotlin.SharedScopes
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.client.C01PacketChatMessage
import net.minecraft.network.play.server.S40PacketDisconnect
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.util.ChatComponentText
import net.minecraft.util.Session
import java.io.File
import java.util.Locale

object AutoAccount :
    Module("AutoAccount", Category.OTHER, subjective = true, gameDetecting = false, hideModule = false) {

    private const val AUTH_WINDOW_MILLIS = 30_000L
    private const val AUTH_SEND_DELAY_MILLIS = 1_000L
    private const val AUTH_RECHECK_DELAY_MILLIS = 3_000L

    private val register by boolean("AutoRegister", true)
    private val login by boolean("AutoLogin", true)
    private val recordPasswords by boolean("RecordPasswords", true)

    // Gamster requires 8 chars+
    private val passwordValue = object : TextValue("Password", "zywl1337#") {
        override fun onChange(oldValue: String, newValue: String) =
            when {
                ' ' in newValue -> {
                    chat("§7[§a§lAutoAccount§7] §cPassword cannot contain a space!")
                    oldValue
                }

                newValue.equals("reset", true) -> {
                    chat("§7[§a§lAutoAccount§7] §3Password reset to its default value.")
                    "axolotlaxolotl"
                }

                else -> super.onChange(oldValue, newValue)
            }

        override fun isSupported() = register || login
    }
    private val password by passwordValue

    // Delay before sending commands
    private val sendDelay by int("SendDelay", 1000, 0..5000) { passwordValue.isSupported() }

    private val autoSession by boolean("AutoSession", false)
    private val startupValue = boolean("RandomAccountOnStart", false) { autoSession }
    private val relogInvalidValue = boolean("RelogWhenPasswordInvalid", true) { autoSession }
    private val relogKickedValue = boolean("RelogWhenKicked", false) { autoSession }

    private val reconnectDelayValue = int("ReconnectDelay", 1000, 0..2500)
    { relogInvalidValue.isActive() || relogKickedValue.isActive() }
    private val reconnectDelay by reconnectDelayValue

    private val accountModeValue = object : ListValue("AccountMode", arrayOf("RandomName", "RandomAlt"), "RandomName") {
        override fun isSupported() = reconnectDelayValue.isSupported() || startupValue.isActive()

        override fun onChange(oldValue: String, newValue: String): String {
            if (newValue == "RandomAlt" && accountsConfig.accounts.filterIsInstance<CrackedAccount>().size <= 1) {
                chat("§7[§a§lAutoAccount§7] §cAdd more cracked accounts in AltManager to use RandomAlt option!")
                return oldValue
            }

            return super.onChange(oldValue, newValue)
        }
    }
    private val accountMode by accountModeValue

    private val saveValue = boolean("SaveToAlts", false) {
        accountModeValue.isSupported() && accountMode != "RandomAlt"
    }

    private var status = Status.WAITING
    private var lastCommand: AuthCommand? = null
    private var authWindowEndsAt = 0L
    private var authAttemptToken = 0L
    private var authSessionCompleted = false

    // Password storage system
    private val serverPasswords = mutableMapOf<String, String>() // serverIP -> password
    private val accountPasswords = mutableMapOf<String, String>() // accountName -> password
    
    // Load passwords from config on module initialization
    override fun onInitialize() {
        super.onInitialize()
        loadPasswords()
    }

    override fun onEnable() {
        super.onEnable()
        if (mc.theWorld != null) {
            resetAuthWindow()
        } else {
            clearAuthWindow()
        }
    }
    
    // Save passwords to config on module disable
    override fun onDisable() {
        super.onDisable()
        clearAuthWindow()
        savePasswords()
    }

    private fun resetAuthWindow() {
        authWindowEndsAt = System.currentTimeMillis() + AUTH_WINDOW_MILLIS
        status = Status.WAITING
        lastCommand = null
        authAttemptToken++
        authSessionCompleted = false
    }

    private fun clearAuthWindow() {
        authWindowEndsAt = 0L
        status = Status.WAITING
        lastCommand = null
        authAttemptToken++
        authSessionCompleted = false
    }

    private fun sleepAuthWindow() {
        authWindowEndsAt = 0L
        status = Status.STOPPED
        lastCommand = null
        authAttemptToken++
        authSessionCompleted = false
    }

    private fun finishAuthSession() {
        authWindowEndsAt = 0L
        status = Status.STOPPED
        lastCommand = null
        authAttemptToken++
        authSessionCompleted = true
    }

    private fun isAuthWindowOpen() = authWindowEndsAt != 0L && System.currentTimeMillis() <= authWindowEndsAt
    
    private fun loadPasswords() {
        try {
            // Load from config file
            val passwordsFile = File(FileManager.dir, "server_passwords.json")
            if (passwordsFile.exists()) {
                val json = FileManager.PRETTY_GSON.fromJson(passwordsFile.reader(), Map::class.java)
                serverPasswords.clear()
                accountPasswords.clear()
                
                json?.let { data ->
                    val serverData = data["serverPasswords"] as? Map<*, *>
                    val accountData = data["accountPasswords"] as? Map<*, *>
                    
                    serverData?.forEach { (key, value) ->
                        if (key is String && value is String) {
                            serverPasswords[key] = value
                        }
                    }
                    
                    accountData?.forEach { (key, value) ->
                        if (key is String && value is String) {
                            accountPasswords[key] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors, start with empty password storage
        }
    }
    
    private fun savePasswords() {
        try {
            val passwordsFile = File(FileManager.dir, "server_passwords.json")
            val data = mapOf(
                "serverPasswords" to serverPasswords,
                "accountPasswords" to accountPasswords
            )
            passwordsFile.writeText(FileManager.PRETTY_GSON.toJson(data))
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
    
    private fun getStoredPassword(serverIP: String?, accountName: String): String? {
        // Use account-specific passwords for auth decisions so one server does not
        // force the wrong branch for a different account on the same server.
        return accountPasswords[accountName]
    }
    
    private fun storePassword(serverIP: String?, accountName: String, password: String) {
        if (recordPasswords) {
            serverIP?.let { ip ->
                serverPasswords[ip] = password
            }
            accountPasswords[accountName] = password
            savePasswords()
        }
    }

    private fun relog(info: String = "") {
        // Disconnect from server
        if (mc.currentServerData != null && mc.theWorld != null)
            mc.netHandler.networkManager.closeChannel(
                ChatComponentText("$info\n\nReconnecting with a random account in ${reconnectDelay}ms")
            )

        // Log in to account with a random name, optionally save it
        changeAccount()

        SharedScopes.IO.launch {
            delay(sendDelay.toLong())
            // connectToLastServer needs thread with OpenGL context
            Minecraft.getMinecraft().addScheduledTask { ServerUtils.connectToLastServer() }
        }
    }

    private fun scheduleAuthCommand(command: String, commandType: AuthCommand) {
        if (status != Status.WAITING) {
            return
        }

        status = Status.PENDING
        lastCommand = commandType

        val token = ++authAttemptToken

        SharedScopes.IO.launch {
            delay(AUTH_SEND_DELAY_MILLIS)

            if (token != authAttemptToken || !state || !isAuthWindowOpen()) {
                return@launch
            }

            Minecraft.getMinecraft().addScheduledTask {
                if (token != authAttemptToken || !state || !isAuthWindowOpen() || status != Status.PENDING) {
                    return@addScheduledTask
                }

                val player = mc.thePlayer ?: return@addScheduledTask
                player.sendChatMessage(command)
                status = Status.SENT_COMMAND
            }

            delay(AUTH_RECHECK_DELAY_MILLIS)

            if (token != authAttemptToken || !state || !isAuthWindowOpen()) {
                return@launch
            }

            Minecraft.getMinecraft().addScheduledTask {
                if (token != authAttemptToken || !state || !isAuthWindowOpen()) {
                    return@addScheduledTask
                }

                if (status == Status.SENT_COMMAND || status == Status.PENDING) {
                    finishAuthSession()
                }
            }
        }
    }

    private fun normalizeMessage(message: String) =
        message.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    private fun isRegisterPrompt(msg: String): Boolean {
        return "/register" in msg ||
            "register first" in msg ||
            "must register" in msg ||
            "please register" in msg ||
            "you must register" in msg
    }

    private fun isLoginPrompt(msg: String): Boolean {
        return "/login" in msg ||
            "login first" in msg ||
            "log in first" in msg ||
            "must log in" in msg ||
            "must login" in msg ||
            "please login" in msg ||
            "please log in" in msg ||
            "you are not logged in" in msg
    }

    private fun isAuthPrompt(msg: String) = isRegisterPrompt(msg) || isLoginPrompt(msg)

    private fun respond(msg: String) = when {
        // Server asking to register - send the register command once after the delay.
        register && isRegisterPrompt(msg) -> {
            lastCommand = AuthCommand.REGISTER
            addNotification(Notification("Processing registration request.", "Auth", Type.INFO))
            scheduleAuthCommand("/register $password $password", AuthCommand.REGISTER)
            true
        }

        // Server asking to login - use stored password if available, otherwise fall back to configured password
        login && isLoginPrompt(msg) -> {
            val serverIP = mc.currentServerData?.serverIP
            val accountName = mc.session.username
            val storedPassword = getStoredPassword(serverIP, accountName)?.takeIf { it.isNotBlank() }
            val passwordToUse = storedPassword ?: password
            lastCommand = AuthCommand.LOGIN

            addNotification(Notification("Processing login request.", "Auth", Type.INFO))
            scheduleAuthCommand("/login $passwordToUse", AuthCommand.LOGIN)
            true
        }

        else -> false
    }

    // Use always = true so events always fire regardless of game state
    val onPacket = handler<PacketEvent>(always = true) { event ->
        if (!isAuthWindowOpen()) {
            if (status != Status.STOPPED) {
                status = Status.STOPPED
            }
            return@handler
        }

        when (val packet = event.packet) {
            is C01PacketChatMessage -> {
                if (!recordPasswords || event.eventType != EventState.SEND) return@handler

                val message = packet.message
                val parts = message.trim().split(" ")

                when {
                    message.startsWith("/register ", ignoreCase = true) || message.startsWith("/reg ", ignoreCase = true) -> {
                        val passwordUsed = parts.getOrNull(1) ?: return@handler
                        storePassword(mc.currentServerData?.serverIP, mc.session.username, passwordUsed)
                        addNotification(Notification("Password recorded for ${mc.session.username}", "Password Saved", Type.SUCCESS))
                    }

                    message.startsWith("/login ", ignoreCase = true) || message.startsWith("/log ", ignoreCase = true) || message.startsWith("/l ", ignoreCase = true) -> {
                        val passwordUsed = parts.getOrNull(1) ?: return@handler
                        storePassword(mc.currentServerData?.serverIP, mc.session.username, passwordUsed)
                        addNotification(Notification("Password recorded for ${mc.session.username}", "Password Saved", Type.SUCCESS))
                    }
                }
            }

            is S02PacketChat, is S45PacketTitle -> {
                // Don't respond to register / login prompts when failed once
                if (!passwordValue.isSupported()) {
                    return@handler
                }

                val msg = when (packet) {
                    is S02PacketChat -> packet.chatComponent?.unformattedText?.let(::normalizeMessage)
                    is S45PacketTitle -> packet.message?.unformattedText?.let(::normalizeMessage)
                    else -> return@handler
                } ?: return@handler

                if (isAuthPrompt(msg) && status == Status.WAITING) {
                    if (respond(msg)) {
                        event.cancelEvent()
                    }
                } else {
                    // Check response from server
                    when {
                        // Logged in
                        "success" in msg || "logged" in msg || "registered" in msg -> {
                            success()
                            event.cancelEvent()
                        }
                        // Login failed, possibly relog
                        "incorrect" in msg || "wrong" in msg || "invalid password" in msg || "spatne" in msg || "does not match" in msg -> fail()
                        "unknown command" in msg || "not a valid command" in msg || "cannot be used from here" in msg || "you are already logged in" in msg -> {
                            // Tried executing /login or /register from lobby, stop trying
                            status = Status.STOPPED
                            event.cancelEvent()
                        }
                    }
                }
            }

            is S40PacketDisconnect -> {
                if (relogKickedValue.isActive() && status != Status.SENT_COMMAND) {
                    val reason = packet.reason.unformattedText
                    if ("ban" in reason) return@handler

                    relog(packet.reason.unformattedText)
                }
            }
        }

    }

    val onWorld = handler<WorldEvent> { event ->
        if (!passwordValue.isSupported()) return@handler

        // Reset the auth window on a fresh join, but keep SENT_COMMAND transitions intact.
        if (event.worldClient != null && status != Status.SENT_COMMAND && !authSessionCompleted) {
            resetAuthWindow()
            return@handler
        }

        if (event.worldClient == null) {
            clearAuthWindow()
            return@handler
        }

        if (status == Status.SENT_COMMAND) {
            // Server redirected the player to a lobby, success
            if (mc.theWorld != event.worldClient) success()
            // Login failed, possibly relog
            else fail()
        }
    }

    val onStartup = handler<StartupEvent> {
        // Log in to account with a random name after startup, optionally save it
        if (startupValue.isActive()) changeAccount()
    }

    // Login succeeded
    private fun success() {
        if (status == Status.SENT_COMMAND) {
            val accountName = mc.session.username
            val serverIP = mc.currentServerData?.serverIP
            
            // Store the password if we just registered successfully
            if (lastCommand == AuthCommand.REGISTER) {
                storePassword(serverIP, accountName, password)
                addNotification(Notification("Registered and logged in as $accountName", "Registered", Type.SUCCESS))
            } else {
                addNotification(Notification("Logged in as $accountName", "Logged", Type.SUCCESS))
            }
            
            // Stop waiting for response
            finishAuthSession()
        }
    }

    // Login failed
    private fun fail() {
        if (status == Status.SENT_COMMAND) {
            addNotification(Notification("Failed to log in as ${mc.session.username}", "ERROR", Type.ERROR))

            // Stop waiting for response
            finishAuthSession()

            // Trigger relog task
            if (relogInvalidValue.isActive()) relog()
        }
    }

    private fun changeAccount() {
        if (accountMode == "RandomAlt") {
            val account = accountsConfig.accounts.filter { it is CrackedAccount && it.name != mc.session.username }
                .randomOrNull() ?: return
            mc.session = Session(
                account.session.username, account.session.uuid,
                account.session.token, account.session.type
            )
            call(SessionUpdateEvent)
            return
        }

        // Log in to account with a random name
        val account = randomAccount()

        // Save as a new account if SaveToAlts is enabled
        if (saveValue.isActive() && !accountsConfig.accountExists(account)) {
            accountsConfig.addAccount(account)
            accountsConfig.saveConfig()

            addNotification(Notification("Saved alt ${account.name}", "Sucess", Type.SUCCESS))
        }
    }

    private enum class Status {
        WAITING, PENDING, SENT_COMMAND, STOPPED
    }

    private enum class AuthCommand {
        LOGIN, REGISTER
    }
    
    // Called from MixinGuiNewChat to process incoming chat messages
    fun onChatMessage(message: String) {
        if (!state || !passwordValue.isSupported() || status != Status.WAITING) return
        
        val msg = normalizeMessage(message)
        
        if (isAuthPrompt(msg) && respond(msg)) return
    }

    val onUpdate = handler<UpdateEvent> {
        if (!state || !passwordValue.isSupported()) {
            return@handler
        }

        if (status == Status.WAITING && authWindowEndsAt != 0L && System.currentTimeMillis() > authWindowEndsAt) {
            sleepAuthWindow()
        }
    }
}
