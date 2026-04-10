/*
 * FDPClient Hacked Client
 */
package net.asd.union.utils.performance

import net.asd.union.features.module.Module
import net.asd.union.features.module.MODULE_REGISTRY
import net.asd.union.utils.client.ClassUtils
import net.asd.union.utils.client.ClientUtils.LOGGER
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object OptimizedModuleLoader {
    
    // Modules to exclude from loading
    private val EXCLUDED_MODULES = setOf(
        "Xray",           // Visual
        "Spammer",        // Other
        "MurderDetector", // Other
        "ChestAura",      // Other
        "ChestStealer",   // Other/Player
        "ClickRecorder",  // Other
        "DrinkingAlert",  // Other
        "AutoRole",       // Other
        "StaffDetector",  // Other
        "PingSpoof",      // Exploit
        "ServerCrasher",  // Exploit
        "Plugins",        // Exploit
        "AntiBot",        // Client
        "SnakeGame",      // Client
        "Ignite",         // Combat
        "HitBox"          // Combat
    )
    
    private val moduleLoadExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    ) { r ->
        Thread(r, "ModuleLoader-${Thread.activeCount()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    
    // Cache for module classes to avoid repeated reflection
    private val moduleClassCache = mutableSetOf<Class<out Module>>()
    
    /**
     * Optimized parallel module loading
     */
    fun registerModulesOptimized() {
        val time = measureTimeMillis {
            LOGGER.info("[OptimizedModuleLoader] Loading modules with parallel processing...")
            
            // Discover module classes (cached)
            val moduleClasses = discoverModuleClasses()
            
            // Load modules in parallel
            loadModulesParallel(moduleClasses)
            
            // Initialize modules in parallel
            initializeModulesParallel()
        }
        
        LOGGER.info("[OptimizedModuleLoader] Loaded ${MODULE_REGISTRY.size} modules in ${time}ms")
    }
    
    /**
     * Discover module classes with caching
     */
    private fun discoverModuleClasses(): List<Class<out Module>> {
        if (moduleClassCache.isNotEmpty()) {
            return moduleClassCache.toList()
        }
        
        val discoveryTime = measureTimeMillis {
            val packageName = "net.asd.union.features.module.modules"
            val classes = ClassUtils.resolvePackage(packageName, Module::class.java)
            // Filter out excluded modules
            moduleClassCache.addAll(classes.filter { it.simpleName !in EXCLUDED_MODULES })
        }
        
        LOGGER.info("[OptimizedModuleLoader] Discovered ${moduleClassCache.size} module classes in ${discoveryTime}ms")
        return moduleClassCache.toList()
    }
    
    /**
     * Load modules in parallel
     */
    private fun loadModulesParallel(moduleClasses: List<Class<out Module>>) {
        val loadedModules = ConcurrentLinkedQueue<Module>()
        val futures = mutableListOf<CompletableFuture<Void>>()
        
        // Split classes into batches for parallel processing
        val batchSize = (moduleClasses.size / Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        val batches = moduleClasses.chunked(batchSize)
        
        for (batch in batches) {
            val future = CompletableFuture.runAsync({
                for (moduleClass in batch) {
                    try {
                        val module = instantiateModule(moduleClass)
                        if (module != null) {
                            loadedModules.offer(module)
                        }
                    } catch (e: Exception) {
                        LOGGER.error("[OptimizedModuleLoader] Failed to load module: ${moduleClass.name} - ${e.message}")
                    }
                }
            }, moduleLoadExecutor)
            
            futures.add(future)
        }
        
        // Wait for all batches to complete
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        
        // Add all loaded modules to registry
        loadedModules.forEach { module ->
            MODULE_REGISTRY.add(module)
        }
        
        LOGGER.info("[OptimizedModuleLoader] Instantiated ${loadedModules.size} modules")
    }
    
    /**
     * Instantiate module with proper error handling
     */
    private fun instantiateModule(moduleClass: Class<out Module>): Module? {
        return try {
            // Try regular instantiation first
            moduleClass.newInstance()
        } catch (e: IllegalAccessException) {
            // Handle Kotlin object modules
            ClassUtils.getObjectInstance(moduleClass) as? Module
        } catch (e: Exception) {
            LOGGER.warn("[OptimizedModuleLoader] Failed to instantiate ${moduleClass.simpleName}: ${e.message}")
            null
        }
    }
    
    /**
     * Initialize modules in parallel
     */
    private fun initializeModulesParallel() {
        val initTime = measureTimeMillis {
            val futures = MODULE_REGISTRY.map { module ->
                CompletableFuture.runAsync({
                    try {
                        module.onInitialize()
                    } catch (e: Exception) {
                        LOGGER.error("[OptimizedModuleLoader] Failed to initialize ${module.name}: ${e.message}")
                    }
                }, moduleLoadExecutor)
            }
            
            // Wait for all initializations to complete with timeout
            try {
                CompletableFuture.allOf(*futures.toTypedArray()).get(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                LOGGER.warn("[OptimizedModuleLoader] Module initialization timeout or error: ${e.message}")
            }
        }
        
        LOGGER.info("[OptimizedModuleLoader] Initialized ${MODULE_REGISTRY.size} modules in ${initTime}ms")
    }
    
    /**
     * Get loading statistics
     */
    fun getLoadingStats(): String {
        return "Modules: ${MODULE_REGISTRY.size} loaded, ${moduleClassCache.size} classes cached"
    }
    
    /**
     * Shutdown the module loader
     */
    fun shutdown() {
        moduleLoadExecutor.shutdown()
        try {
            if (!moduleLoadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                moduleLoadExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            moduleLoadExecutor.shutdownNow()
        }
    }
}