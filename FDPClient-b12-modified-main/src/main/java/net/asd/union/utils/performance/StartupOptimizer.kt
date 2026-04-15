/*
 * FDPClient Hacked Client - Startup Performance Optimizer
 * Optimizes client startup time by implementing various performance improvements
 */
package net.asd.union.utils.performance

import net.asd.union.utils.client.ClientUtils.LOGGER
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object StartupOptimizer {
    
    private val optimizationExecutor = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "StartupOptimizer-${Thread.activeCount()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    
    /**
     * Skip unnecessary network requests during startup
     */
    var skipNetworkRequests = true
    
    /**
     * Whether network is blocked during startup (true = blocked, false = allowed)
     */
    var isNetworkBlocked = true
    
    /**
     * Use minimal font set for faster loading
     */
    var useMinimalFonts = true
    
    /**
     * Skip broken language files
     */
    var skipBrokenLanguages = true
    
    /**
     * Cache font extraction status
     */
    private val fontExtractionCache = mutableSetOf<String>()
    
    /**
     * Optimize startup process
     */
    fun optimizeStartup() {
        val time = measureTimeMillis {
            LOGGER.info("[StartupOptimizer] Applying startup optimizations...")
            
            // Pre-warm JVM
            preWarmJVM()
            
            // Set system properties for better performance
            setPerformanceProperties()
            
            LOGGER.info("[StartupOptimizer] Startup optimizations applied")
        }
        
        LOGGER.info("[StartupOptimizer] Optimization setup completed in ${time}ms")
    }
    
    /**
     * Pre-warm JVM for better performance
     */
    private fun preWarmJVM() {
        // Trigger JIT compilation for common operations
        repeat(1000) {
            val list = mutableListOf<String>()
            list.add("warmup")
            list.removeAt(0)
        }
        
        // Force garbage collection
        System.gc()
    }
    
    /**
     * Set system properties for better performance
     */
    private fun setPerformanceProperties() {
        // Disable unnecessary Java features
        System.setProperty("java.awt.headless", "false")
        System.setProperty("sun.java2d.opengl", "true")
        System.setProperty("sun.java2d.d3d", "false")
        
        // Optimize networking
        System.setProperty("java.net.useSystemProxies", "false")
        System.setProperty("networkaddress.cache.ttl", "300")

        // Never force a platform-specific file system provider.
        val providerProperty = "java.nio.file.spi.DefaultFileSystemProvider"
        val currentProvider = System.getProperty(providerProperty)
        val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)

        if (!isWindows && currentProvider == "sun.nio.fs.WindowsFileSystemProvider") {
            LOGGER.warn("[StartupOptimizer] Clearing Windows-only file system provider override on non-Windows OS")
            System.clearProperty(providerProperty)
        }
    }
    
    /**
     * Check if fonts are already extracted to avoid duplicate extraction
     */
    fun isFontExtracted(fontFile: String): Boolean {
        return fontExtractionCache.contains(fontFile)
    }
    
    /**
     * Mark font as extracted
     */
    fun markFontExtracted(fontFile: String) {
        fontExtractionCache.add(fontFile)
    }
    
    /**
     * Block all network requests by setting system properties
     */
    fun blockNetwork() {
        isNetworkBlocked = true
        System.setProperty("http.proxyHost", "localhost")
        System.setProperty("http.proxyPort", "1")
        System.setProperty("https.proxyHost", "localhost")
        System.setProperty("https.proxyPort", "1")
        System.setProperty("ftp.proxyHost", "localhost")
        System.setProperty("ftp.proxyPort", "1")
        System.setProperty("java.net.preferIPv4Stack", "true")
        LOGGER.info("[StartupOptimizer] Network requests blocked")
    }
    
    /**
     * Unblock all network requests by clearing system properties
     */
    fun unblockNetwork() {
        isNetworkBlocked = false
        System.clearProperty("http.proxyHost")
        System.clearProperty("http.proxyPort")
        System.clearProperty("https.proxyHost")
        System.clearProperty("https.proxyPort")
        System.clearProperty("ftp.proxyHost")
        System.clearProperty("ftp.proxyPort")
        LOGGER.info("[StartupOptimizer] Network requests unblocked")
    }
    
    /**
     * Execute task asynchronously with timeout
     */
    fun <T> executeAsync(timeout: Long = 5000, task: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        
        optimizationExecutor.submit {
            try {
                val result = task()
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        
        // Set timeout
        optimizationExecutor.schedule({
            if (!future.isDone) {
                future.completeExceptionally(RuntimeException("Task timed out after ${timeout}ms"))
            }
        }, timeout, TimeUnit.MILLISECONDS)
        
        return future
    }
    
    /**
     * Shutdown optimizer
     */
    fun shutdown() {
        optimizationExecutor.shutdown()
        try {
            if (!optimizationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                optimizationExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            optimizationExecutor.shutdownNow()
        }
    }
}
