/*
 * FDPClient Hacked Client - Optimized Language Loader
 * Skips broken language files and loads only essential languages
 */
package net.asd.union.utils.performance

import com.google.gson.JsonSyntaxException
import net.asd.union.utils.client.ClientUtils.LOGGER
import kotlin.system.measureTimeMillis

object OptimizedLanguageLoader {
    
    // Essential languages only - reduces loading time
    private val essentialLanguages = setOf(
        "en_US", // English (US) - Primary
        "pt_BR", // Portuguese (Brazil)
        "pt_PT", // Portuguese (Portugal)
        "zh_TW", // Chinese (Traditional) - Working version
        "ru_RU", // Russian
        "bg_BG"  // Bulgarian
    )
    
    // Known broken language files to skip
    private val brokenLanguages = setOf(
        "zh_CN" // Chinese (Simplified) - Has JSON syntax error
    )
    
    /**
     * Load languages with error handling and filtering
     */
    fun loadLanguagesOptimized(originalLoader: () -> Unit) {
        val time = measureTimeMillis {
            LOGGER.info("[OptimizedLanguageLoader] Loading essential languages...")
            
            try {
                // Call original loader but with error handling
                originalLoader()
            } catch (e: Exception) {
                LOGGER.warn("[OptimizedLanguageLoader] Language loading completed with some errors: ${e.message}")
            }
        }
        
        LOGGER.info("[OptimizedLanguageLoader] Language loading completed in ${time}ms")
    }
    
    /**
     * Check if language should be loaded
     */
    fun shouldLoadLanguage(languageCode: String): Boolean {
        // Skip known broken languages
        if (brokenLanguages.contains(languageCode)) {
            LOGGER.info("[OptimizedLanguageLoader] Skipping broken language: $languageCode")
            return false
        }
        
        // Load only essential languages for faster startup
        if (StartupOptimizer.useMinimalFonts && !essentialLanguages.contains(languageCode)) {
            LOGGER.debug("[OptimizedLanguageLoader] Skipping non-essential language: $languageCode")
            return false
        }
        
        return true
    }
    
    /**
     * Handle language loading with proper error recovery
     */
    fun loadLanguageWithErrorHandling(languageCode: String, loader: () -> Unit): Boolean {
        return try {
            if (!shouldLoadLanguage(languageCode)) {
                return false
            }
            
            loader()
            LOGGER.info("[OptimizedLanguageLoader] Loaded language $languageCode")
            true
        } catch (e: JsonSyntaxException) {
            LOGGER.error("[OptimizedLanguageLoader] Failed to load language $languageCode: JSON syntax error at ${e.message}")
            // Add to broken languages list for future runs
            (brokenLanguages as MutableSet).add(languageCode)
            false
        } catch (e: Exception) {
            LOGGER.error("[OptimizedLanguageLoader] Failed to load language $languageCode: ${e.message}")
            false
        }
    }
    
    /**
     * Get language loading statistics
     */
    fun getLanguageStats(): String {
        return "Languages: ${essentialLanguages.size} essential, ${brokenLanguages.size} broken/skipped"
    }
}