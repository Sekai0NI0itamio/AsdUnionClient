/*
 * FDPClient Hacked Client
 */
package net.asd.union.utils.performance

import net.asd.union.features.module.Module
import net.asd.union.features.module.MODULE_REGISTRY
import net.asd.union.features.module.ModuleManager
import net.asd.union.utils.client.ClassUtils
import net.asd.union.utils.client.ClientUtils.LOGGER
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

object OptimizedModuleLoader {
    private const val LOAD_PHASE_WEIGHT = 0.7f
    
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
            try {
                LOGGER.info("[OptimizedModuleLoader] Loading modules with parallel processing...")

                // Discover module classes (cached)
                val moduleClasses = discoverModuleClasses()

                // Load modules in parallel
                loadModulesParallel(moduleClasses)

                // Initialize modules in parallel
                initializeModulesParallel()

                if (MODULE_REGISTRY.isEmpty() && moduleClasses.isNotEmpty()) {
                    LOGGER.warn("[OptimizedModuleLoader] Optimized load returned 0 modules, retrying sequential safe mode")
                    registerModulesSequentialSafe()
                }
            } catch (t: Throwable) {
                LOGGER.error("[OptimizedModuleLoader] Optimized module loading failed, falling back to safe mode", t)
                MODULE_REGISTRY.clear()
                registerModulesSequentialSafe()
            }
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
            try {
                val packageName = "net.asd.union.features.module.modules"
                val classes = ClassUtils.resolvePackage(packageName, Module::class.java)
                // Filter out excluded modules
                moduleClassCache.addAll(classes.filter { it.simpleName !in EXCLUDED_MODULES })
            } catch (t: Throwable) {
                LOGGER.error("[OptimizedModuleLoader] Failed to discover module classes", t)
            }
        }
        
        LOGGER.info("[OptimizedModuleLoader] Discovered ${moduleClassCache.size} module classes in ${discoveryTime}ms")
        return moduleClassCache.toList()
    }
    
    /**
     * Load modules in parallel
     */
    private fun loadModulesParallel(moduleClasses: List<Class<out Module>>) {
        val loadedModules = ConcurrentHashMap<String, Module>()
        val futures = mutableListOf<CompletableFuture<Void>>()
        val totalModules = moduleClasses.size.coerceAtLeast(1)
        
        // Split classes into batches for parallel processing
        val batchSize = (moduleClasses.size / Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
        val batches = moduleClasses.chunked(batchSize)
        
        for (batch in batches) {
            val future = CompletableFuture.runAsync({
                for (moduleClass in batch) {
                    try {
                        val module = instantiateModule(moduleClass)
                        if (module != null) {
                            loadedModules.putIfAbsent(moduleClass.name, module)
                        }
                    } catch (t: Throwable) {
                        LOGGER.error("[OptimizedModuleLoader] Failed to load module: ${moduleClass.name}", t)
                    }
                }
            }, moduleLoadExecutor)
            
            futures.add(future)
        }
        
        val allBatches = CompletableFuture.allOf(*futures.toTypedArray())

        try {
            while (true) {
                val progress = loadedModules.size.toFloat() / totalModules.toFloat()
                StartupProgress.updateSubProgress(progress * LOAD_PHASE_WEIGHT)
                StartupProgressRenderer.render()

                try {
                    allBatches.get(50L, TimeUnit.MILLISECONDS)
                    break
                } catch (_: TimeoutException) {
                }
            }
        } catch (t: Throwable) {
            LOGGER.error("[OptimizedModuleLoader] Parallel module loading failed", t)
        }

        var recoveredCount = 0
        for (moduleClass in moduleClasses) {
            if (loadedModules.containsKey(moduleClass.name)) {
                continue
            }

            val recovered = instantiateModule(moduleClass) ?: continue
            loadedModules.putIfAbsent(moduleClass.name, recovered)
            recoveredCount++
        }

        if (recoveredCount > 0) {
            LOGGER.info("[OptimizedModuleLoader] Recovered ${recoveredCount} module(s) with sequential retry")
        }
        
        // Add all loaded modules to registry
        loadedModules.values.forEach { module ->
            ModuleManager.registerModule(module)
        }

        StartupProgress.updateSubProgress(LOAD_PHASE_WEIGHT)
        StartupProgressRenderer.render()
        
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
        } catch (t: Throwable) {
            LOGGER.warn("[OptimizedModuleLoader] Failed to instantiate ${moduleClass.simpleName}", t)
            null
        }
    }
    
    /**
     * Initialize modules in parallel
     */
    private fun initializeModulesParallel() {
        val initTime = measureTimeMillis {
            val totalModules = MODULE_REGISTRY.size.coerceAtLeast(1)
            val initializedCount = AtomicInteger(0)
            val futures = MODULE_REGISTRY.map { module ->
                CompletableFuture.runAsync({
                    try {
                        module.onInitialize()
                    } catch (t: Throwable) {
                        LOGGER.error("[OptimizedModuleLoader] Failed to initialize ${module.name}", t)
                    } finally {
                        initializedCount.incrementAndGet()
                    }
                }, moduleLoadExecutor)
            }
            
            // Wait for all initializations to complete with timeout
            try {
                val allInit = CompletableFuture.allOf(*futures.toTypedArray())
                val deadline = System.currentTimeMillis() + 10_000L

                while (true) {
                    val progress = initializedCount.get().toFloat() / totalModules.toFloat()
                    StartupProgress.updateSubProgress(LOAD_PHASE_WEIGHT + progress * (1f - LOAD_PHASE_WEIGHT))
                    StartupProgressRenderer.render()

                    try {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0L) {
                            throw TimeoutException("Module init timed out")
                        }

                        allInit.get(minOf(remaining, 50L), TimeUnit.MILLISECONDS)
                        break
                    } catch (_: TimeoutException) {
                    }
                }
            } catch (t: Throwable) {
                LOGGER.warn("[OptimizedModuleLoader] Module initialization timeout or error", t)
            }
        }
        
        LOGGER.info("[OptimizedModuleLoader] Initialized ${MODULE_REGISTRY.size} modules in ${initTime}ms")
        StartupProgress.updateSubProgress(1f)
        StartupProgressRenderer.render()
    }

    private fun registerModulesSequentialSafe() {
        val time = measureTimeMillis {
            val moduleClasses = discoverModuleClasses()
            val totalModules = moduleClasses.size.coerceAtLeast(1)
            var processed = 0

            for (moduleClass in moduleClasses) {
                val module = instantiateModule(moduleClass) ?: continue
                ModuleManager.registerModule(module)
                processed++
                StartupProgress.updateSubProgress(processed.toFloat() / totalModules.toFloat() * LOAD_PHASE_WEIGHT)
                StartupProgressRenderer.render()
            }

            for (module in MODULE_REGISTRY) {
                try {
                    module.onInitialize()
                } catch (t: Throwable) {
                    LOGGER.error("[OptimizedModuleLoader] Failed to initialize ${module.name} (sequential)", t)
                }

                processed++
                StartupProgress.updateSubProgress(
                    LOAD_PHASE_WEIGHT + processed.toFloat() / (totalModules.toFloat() * 2f) * (1f - LOAD_PHASE_WEIGHT),
                )
                StartupProgressRenderer.render()
            }
        }

        LOGGER.info("[OptimizedModuleLoader] Sequential load completed in ${time}ms")
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
