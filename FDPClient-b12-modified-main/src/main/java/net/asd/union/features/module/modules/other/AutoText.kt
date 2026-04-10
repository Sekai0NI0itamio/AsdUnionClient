/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.other

import net.asd.union.config.*
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.client.chat
import net.asd.union.utils.timing.MSTimer
import java.util.concurrent.ThreadLocalRandom

object AutoText : Module("AutoText", Category.OTHER, subjective = true, hideModule = false) {

    // Message input for GUI
    private val addMessageValue = TextValue("AddMessage", "")
    
    // Message list storage
    private val messages = mutableListOf<String>()
    
    // Mode selection
    private val mode by choices("Mode", arrayOf("FullRandom", "RangedRandom", "Single"), "FullRandom")
    
    // Random logic mode
    private val randomLogic by choices("RandomLogic", arrayOf("Independent", "Dependent"), "Independent") { mode != "Single" }
    
    // Range settings for RangedRandom mode
    private val rangeMaxValue: IntegerValue = object : IntegerValue("RangeMax", 5, 1..100) {
        override fun isSupported() = mode == "RangedRandom"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(rangeMin)
    }
    private val rangeMax by rangeMaxValue
    
    private val rangeMin: Int by object : IntegerValue("RangeMin", 1, 1..100) {
        override fun isSupported() = mode == "RangedRandom"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(rangeMax)
    }
    
    // Single mode settings
    private val singleId by object : IntegerValue("SingleID", 1, 1..100) {
        override fun isSupported() = mode == "Single"
    }
    
    // Delay mode
    private val delayMode by choices("DelayMode", arrayOf("Constant", "Ranged"), "Constant")
    
    // Constant delay (in seconds)
    private val constantDelay by object : FloatValue("ConstantDelay", 2.4f, 0.1f..60f, "s") {
        override fun isSupported() = delayMode == "Constant"
    }
    
    // Ranged delay (in seconds)
    private val delayMaxValue: FloatValue = object : FloatValue("DelayMax", 3.0f, 0.1f..60f, "s") {
        override fun isSupported() = delayMode == "Ranged"
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(delayMin)
    }
    private val delayMax by delayMaxValue
    
    private val delayMin: Float by object : FloatValue("DelayMin", 1.0f, 0.1f..60f, "s") {
        override fun isSupported() = delayMode == "Ranged"
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(delayMax)
    }
    
    // Anti-spam settings
    private val antiSpam by boolean("AntiSpam", false)
    private val attachMessage by boolean("AttachMessage", false) { antiSpam }
    private val attachText by text("AttachText", "&&&&hi&&&& && word && hello! &&& ma&&") { antiSpam && attachMessage }
    
    // Precomputed random chars for optimization
    private val randomChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val randomCharsLength = randomChars.length
    
    // Timer and state
    private val msTimer = MSTimer()
    private var currentDelay = 0L
    private var dependentCache = mutableListOf<Int>()
    
    override fun onEnable() {
        resetState()
        if (messages.isEmpty()) {
            chat("§c§lAutoText §7» §cNo messages in list! Use .autotext add <message> to add messages.")
        }
    }
    
    override fun onDisable() {
        resetState()
    }
    
    private fun resetState() {
        msTimer.reset()
        currentDelay = calculateDelay()
        dependentCache.clear()
    }
    
    private fun calculateDelay(): Long {
        return when (delayMode) {
            "Constant" -> (constantDelay * 1000).toLong()
            "Ranged" -> {
                val random = ThreadLocalRandom.current()
                ((random.nextFloat() * (delayMax - delayMin) + delayMin) * 1000).toLong()
            }
            else -> 2400L
        }
    }
    
    val onUpdate = handler<UpdateEvent> {
        if (messages.isEmpty()) return@handler
        
        if (msTimer.hasTimePassed(currentDelay)) {
            val messageToSend = getNextMessage() ?: return@handler
            
            // Process and send message
            val processedMessage = processMessage(messageToSend)
            mc.thePlayer?.sendChatMessage(processedMessage)
            
            // Reset timer and calculate new delay
            msTimer.reset()
            currentDelay = calculateDelay()
        }
    }
    
    private fun getNextMessage(): String? {
        if (messages.isEmpty()) return null
        
        return when (mode) {
            "FullRandom" -> getRandomMessage(0, messages.size - 1)
            "RangedRandom" -> {
                val min = (rangeMin - 1).coerceIn(0, messages.size - 1)
                val max = (rangeMax - 1).coerceIn(0, messages.size - 1)
                if (min > max) return null
                getRandomMessage(min, max)
            }
            "Single" -> {
                val index = singleId - 1
                if (index in messages.indices) messages[index] else null
            }
            else -> null
        }
    }
    
    private fun getRandomMessage(minIndex: Int, maxIndex: Int): String? {
        if (minIndex > maxIndex || minIndex < 0 || maxIndex >= messages.size) return null
        
        return when (randomLogic) {
            "Independent" -> {
                val index = ThreadLocalRandom.current().nextInt(minIndex, maxIndex + 1)
                messages[index]
            }
            "Dependent" -> {
                // Initialize cache if empty
                if (dependentCache.isEmpty()) {
                    dependentCache = (minIndex..maxIndex).toMutableList()
                }
                
                if (dependentCache.isEmpty()) {
                    // Reset cache when all messages have been sent
                    dependentCache = (minIndex..maxIndex).toMutableList()
                }
                
                val randomCacheIndex = ThreadLocalRandom.current().nextInt(dependentCache.size)
                val messageIndex = dependentCache.removeAt(randomCacheIndex)
                messages[messageIndex]
            }
            else -> messages[minIndex]
        }
    }
    
    /**
     * Process message with anti-spam if enabled
     * Optimized for performance with StringBuilder and precomputed random chars
     */
    private fun processMessage(message: String): String {
        if (!antiSpam) return message
        
        val finalMessage = if (attachMessage) {
            message + attachText
        } else {
            message
        }
        
        return replaceAmpersands(finalMessage)
    }
    
    /**
     * Optimized ampersand replacement using StringBuilder
     * Replaces each '&' with a random alphanumeric character
     */
    private fun replaceAmpersands(text: String): String {
        val random = ThreadLocalRandom.current()
        val result = StringBuilder(text.length)
        
        for (char in text) {
            if (char == '&') {
                result.append(randomChars[random.nextInt(randomCharsLength)])
            } else {
                result.append(char)
            }
        }
        
        return result.toString()
    }
    
    // Public API for commands
    fun addMessage(message: String): Boolean {
        if (message.isBlank()) return false
        messages.add(message)
        // Reset dependent cache when messages change
        dependentCache.clear()
        return true
    }
    
    fun removeMessage(id: Int): Boolean {
        val index = id - 1
        if (index !in messages.indices) return false
        messages.removeAt(index)
        dependentCache.clear()
        return true
    }
    
    fun clearMessages() {
        messages.clear()
        dependentCache.clear()
    }
    
    fun getMessages(): List<Pair<Int, String>> {
        return messages.mapIndexed { index, message -> (index + 1) to message }
    }
    
    fun getMessageCount(): Int = messages.size
}
