/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module

import net.asd.union.event.handler
import net.asd.union.event.EventManager.unregisterListener
import net.asd.union.event.KeyEvent
import net.asd.union.event.Listenable
import net.asd.union.features.command.CommandManager.registerCommand
import net.asd.union.utils.client.ClassUtils
import net.asd.union.utils.client.ClientUtils.LOGGER
import java.util.*

val MODULE_REGISTRY = TreeSet(Comparator.comparing(Module::name))

object ModuleManager : Listenable, Collection<Module> by MODULE_REGISTRY {

    fun getModuleInCategory(category: Category) = MODULE_REGISTRY.filter { it.category == category }

    /**
     * Register all modules
     */
    fun registerModules() {
        // Use optimized module loader
        net.asd.union.utils.performance.OptimizedModuleLoader.registerModulesOptimized()
    }

    /**
     * Register [module]
     */
    fun registerModule(module: Module) {
        MODULE_REGISTRY += module
        generateCommand(module)
    }

    /**
     * Register a list of modules
     */
    @SafeVarargs
    fun registerModules(vararg modules: Module) = modules.forEach(this::registerModule)

    /**
     * Unregister module
     */
    fun unregisterModule(module: Module) {
        MODULE_REGISTRY.remove(module)
        unregisterListener(module)
    }

    /**
     * Generate command for [module]
     */
    internal fun generateCommand(module: Module) {
        val values = module.values

        if (values.isEmpty())
            return

        registerCommand(ModuleCommand(module, values))
    }

    /**
     * Get module by [moduleClass]
     */
    operator fun get(moduleClass: Class<out Module>) = MODULE_REGISTRY.find { it.javaClass === moduleClass }

    /**
     * Get module by [moduleName]
     */
    operator fun get(moduleName: String) = MODULE_REGISTRY.find { it.name.equals(moduleName, ignoreCase = true) }
    @Deprecated(message = "Only for outdated scripts", replaceWith = ReplaceWith("get(moduleClass)"))
    fun getModule(moduleClass: Class<out Module>) = get(moduleClass)
    @Deprecated(message = "Only for outdated scripts", replaceWith = ReplaceWith("get(moduleName)"))
    fun getModule(moduleName: String) = get(moduleName)

    fun getKeyBind(key: Int) = MODULE_REGISTRY.filter { it.keyBind == key }

    /**
     * Handle incoming key presses
     */
    private val onKey = handler<KeyEvent> { event ->
        MODULE_REGISTRY.forEach { if (it.keyBind == event.key) it.toggle() }
    }

}