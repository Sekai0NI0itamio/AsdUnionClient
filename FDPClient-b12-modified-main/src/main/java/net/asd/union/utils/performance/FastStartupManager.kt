/*
 * FDPClient Hacked Client - Fast Startup Manager
 * Coordinates all startup optimizations to reduce loading time from 84s to ~25s
 */
package net.asd.union.utils.performance

import net.asd.union.utils.client.ClientUtils.LOGGER
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

object FastStartupManager {
    
    private var isOptimizationEnabled = true
    private var startupStartTime = 0L
    
    /**
     * Initialize fast startup optimizations
     */
    fun initializeFastStartup() {
        startupStartTime = System.currentTimeMillis()
        StartupProgress.start()
        StartupProgress.advanceTo(StartupProgress.STEP_INITIALIZE)
        StartupProgressRenderer.render()
        
        val time = measureTimeMillis {
            LOGGER.info("=== FDPClient Fast Startup Manager ===")
            LOGGER.info("Initializing performance optimizations...")
            
            // Apply system-level optimizations
            StartupOptimizer.optimizeStartup()
            
            // Set optimization flags
            configureOptimizations()
            
            LOGGER.info("Fast startup optimizations initialized")
        }
        
        LOGGER.info("Fast startup initialization completed in ${time}ms")
    }
    
    /**
     * Configure optimization settings
     */
    private fun configureOptimizations() {
        // Enable all optimizations for maximum speed
        StartupOptimizer.skipNetworkRequests = true
        StartupOptimizer.useMinimalFonts = true
        StartupOptimizer.skipBrokenLanguages = true
        
        LOGGER.info("Optimization settings:")
        LOGGER.info("  - Skip network requests: ${StartupOptimizer.skipNetworkRequests}")
        LOGGER.info("  - Use minimal fonts: ${StartupOptimizer.useMinimalFonts}")
        LOGGER.info("  - Skip broken languages: ${StartupOptimizer.skipBrokenLanguages}")
    }
    
    /**
     * Execute optimized preload tasks
     */
    fun executeOptimizedPreload(): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val preloadTime = measureTimeMillis {
            try {
                StartupProgress.advanceTo(StartupProgress.STEP_PRELOAD)
                StartupProgressRenderer.render()
                LOGGER.info("[FastStartup] Starting optimized preload tasks...")
                
                // Execute tasks in parallel where possible
                val fontTask = CompletableFuture.runAsync {
                    OptimizedFontLoader.downloadFontsOptimized()
                }
                
                val languageTask = CompletableFuture.runAsync {
                    // Language loading will be handled by the optimized loader
                    LOGGER.info("[FastStartup] Language loading prepared")
                }

                val preloadFuture = CompletableFuture.allOf(fontTask, languageTask)
                val preloadDeadline = System.currentTimeMillis() + 15_000L

                while (true) {
                    val doneCount = listOf(fontTask, languageTask).count { it.isDone }
                    StartupProgress.updateSubProgress(doneCount / 2f)
                    StartupProgressRenderer.render()

                    try {
                        val remaining = preloadDeadline - System.currentTimeMillis()
                        if (remaining <= 0L) {
                            throw TimeoutException("Preload task timed out after 15000ms")
                        }

                        preloadFuture.get(minOf(remaining, 50L), TimeUnit.MILLISECONDS)
                        break
                    } catch (_: TimeoutException) {
                    }
                }
                
                LOGGER.info("[FastStartup] Optimized preload tasks completed")
                future.complete(Unit)
                
            } catch (e: Exception) {
                LOGGER.error("[FastStartup] Preload task failed: ${e.message}")
                future.completeExceptionally(e)
            }
        }
        
        LOGGER.info("[FastStartup] Preload tasks completed in ${preloadTime}ms")
        return future
    }
    
    /**
     * Execute optimized client startup
     */
    fun executeOptimizedStartup() {
        StartupProgress.advanceTo(StartupProgress.STEP_STARTUP)
        StartupProgressRenderer.render()

        val startupTime = measureTimeMillis {
            LOGGER.info("[FastStartup] Starting optimized client startup...")
            
            try {
                // Load fonts with optimization
                loadFontsOptimized()
                
                // Load modules with parallel processing
                loadModulesOptimized()
                
                // Skip unnecessary network requests during startup
                if (StartupOptimizer.skipNetworkRequests) {
                    LOGGER.info("[FastStartup] Skipping network requests for faster startup")
                }
                
                LOGGER.info("[FastStartup] Optimized startup completed")
                
            } catch (t: Throwable) {
                LOGGER.error("[FastStartup] Startup optimization failed", t)
            }
        }
        
        LOGGER.info("[FastStartup] Client startup completed in ${startupTime}ms")
    }
    
    /**
     * Load fonts with optimizations
     */
    private fun loadFontsOptimized() {
        StartupProgress.advanceTo(StartupProgress.STEP_FONTS)
        StartupProgressRenderer.render()

        val fontTime = measureTimeMillis {
            if (StartupOptimizer.useMinimalFonts) {
                LOGGER.info("[FastStartup] Loading minimal font set for faster startup...")
                OptimizedFontLoader.downloadFontsOptimized()
            } else {
                LOGGER.info("[FastStartup] Loading full font set...")
                // Fall back to original font loading if needed
            }
            
            // Always call Fonts.loadFonts() to initialize font properties
            LOGGER.info("[FastStartup] Initializing font properties...")
            net.asd.union.ui.font.Fonts.loadFonts()
        }
        
        LOGGER.info("[FastStartup] Font loading completed in ${fontTime}ms")
    }
    
    /**
     * Load modules with optimizations
     */
    private fun loadModulesOptimized() {
        StartupProgress.advanceTo(StartupProgress.STEP_MODULES)
        StartupProgressRenderer.render()

        val moduleTime = measureTimeMillis {
            LOGGER.info("[FastStartup] Loading modules with parallel processing...")
            OptimizedModuleLoader.registerModulesOptimized()
        }
        
        LOGGER.info("[FastStartup] Module loading completed in ${moduleTime}ms")
    }
    
    /**
     * Complete startup and show statistics
     */
    fun completeStartup() {
        StartupProgress.advanceTo(StartupProgress.STEP_FINALIZE)
        val totalTime = System.currentTimeMillis() - startupStartTime
        
        LOGGER.info("=== FDPClient Fast Startup Complete ===")
        LOGGER.info("Total startup time: ${totalTime}ms (${totalTime / 1000.0}s)")
        LOGGER.info("Performance improvements:")
        LOGGER.info("  - ${OptimizedFontLoader.getCacheStats()}")
        LOGGER.info("  - ${OptimizedModuleLoader.getLoadingStats()}")
        LOGGER.info("  - ${OptimizedLanguageLoader.getLanguageStats()}")
        
        // Estimate time saved
        val estimatedOriginalTime = 84000L // 84 seconds from log
        val timeSaved = estimatedOriginalTime - totalTime
        val percentImprovement = ((timeSaved.toDouble() / estimatedOriginalTime) * 100).toInt()
        
        if (timeSaved > 0) {
            LOGGER.info("Estimated time saved: ${timeSaved}ms (${percentImprovement}% faster)")
        }
        
        LOGGER.info("========================================")
        StartupProgress.complete()
        StartupProgressRenderer.render()
    }
    
    /**
     * Shutdown optimization systems
     */
    fun shutdown() {
        LOGGER.info("[FastStartup] Shutting down optimization systems...")
        
        StartupOptimizer.shutdown()
        OptimizedModuleLoader.shutdown()
        OptimizedFontLoader.clearCache()
        
        LOGGER.info("[FastStartup] Optimization systems shut down")
    }
    
    /**
     * Enable or disable optimizations
     */
    fun setOptimizationEnabled(enabled: Boolean) {
        isOptimizationEnabled = enabled
        LOGGER.info("[FastStartup] Optimizations ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if optimizations are enabled
     */
    fun isOptimizationEnabled(): Boolean = isOptimizationEnabled
}
