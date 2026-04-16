package net.asd.union.handler.sessiontabs

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.asd.union.FDPClient
import net.asd.union.FDPClient.moduleManager
import net.asd.union.features.module.modules.client.TargetModule
import net.asd.union.file.FileManager
import net.asd.union.utils.io.readJson

data class TabConfigSnapshot(
    val modules: JsonObject,
    val values: JsonObject
) {

    fun applyToClient() {
        val previousLoadingState = FDPClient.isLoadingConfig
        FDPClient.isLoadingConfig = true

        try {
            applyTargets()
            applyModuleStates()
            applyModuleValues()
        } finally {
            FDPClient.isLoadingConfig = previousLoadingState
        }
    }

    private fun applyTargets() {
        val targets = values.getAsJsonObject("targets") ?: return

        if (targets.has("TargetPlayer")) {
            TargetModule.playerValue = targets["TargetPlayer"].asBoolean
        }

        if (targets.has("TargetAnimals")) {
            TargetModule.animalValue = targets["TargetAnimals"].asBoolean
        }

        if (targets.has("TargetMobs")) {
            TargetModule.mobValue = targets["TargetMobs"].asBoolean
        }

        if (targets.has("TargetInvisible")) {
            TargetModule.invisibleValue = targets["TargetInvisible"].asBoolean
        }

        if (targets.has("TargetDead")) {
            TargetModule.deadValue = targets["TargetDead"].asBoolean
        }
    }

    private fun applyModuleStates() {
        for (module in moduleManager) {
            val jsonModule = modules.getAsJsonObject(module.name) ?: continue

            if (jsonModule.has("State")) {
                module.state = jsonModule["State"].asBoolean
            }

            if (jsonModule.has("KeyBind")) {
                module.keyBind = jsonModule["KeyBind"].asInt
            }

            if (jsonModule.has("Array")) {
                module.inArray = jsonModule["Array"].asBoolean
            }
        }
    }

    private fun applyModuleValues() {
        for (module in moduleManager) {
            val jsonModule = values.getAsJsonObject(module.name) ?: continue

            for (moduleValue in module.values) {
                val element = jsonModule.get(moduleValue.name) ?: continue
                moduleValue.fromJson(element)
            }
        }
    }

    companion object {
        private fun cloneJsonObject(jsonObject: JsonObject): JsonObject {
            return JsonParser().parse(jsonObject.toString()).asJsonObject
        }

        fun captureCurrentState(): TabConfigSnapshot {
            val modulesJson = JsonObject()
            val valuesJson = JsonObject()

            val targetsJson = JsonObject().apply {
                addProperty("TargetPlayer", TargetModule.playerValue)
                addProperty("TargetAnimals", TargetModule.animalValue)
                addProperty("TargetMobs", TargetModule.mobValue)
                addProperty("TargetInvisible", TargetModule.invisibleValue)
                addProperty("TargetDead", TargetModule.deadValue)
            }

            valuesJson.add("targets", targetsJson)

            for (module in moduleManager) {
                val jsonModule = JsonObject().apply {
                    addProperty("State", module.state)
                    addProperty("KeyBind", module.keyBind)
                    addProperty("Array", module.inArray)
                }

                modulesJson.add(module.name, jsonModule)

                if (module.values.isEmpty()) {
                    continue
                }

                val jsonValues = JsonObject()

                for (value in module.values) {
                    value.toJson()?.let { jsonValues.add(value.name, it) }
                }

                valuesJson.add(module.name, jsonValues)
            }

            return TabConfigSnapshot(modulesJson, valuesJson)
        }

        fun fromSavedMainStorage(): TabConfigSnapshot {
            val fallback = captureCurrentState()
            val modulesJson = cloneJsonObject(fallback.modules)
            val valuesJson = cloneJsonObject(fallback.values)

            runCatching {
                if (FileManager.modulesConfig.hasConfig()) {
                    val storedModules = FileManager.modulesConfig.file.readJson() as? JsonObject ?: return@runCatching

                    for (module in moduleManager) {
                        val jsonModule = storedModules.getAsJsonObject(module.name) ?: continue
                        modulesJson.add(module.name, cloneJsonObject(jsonModule))
                    }
                }
            }

            runCatching {
                if (FileManager.valuesConfig.hasConfig()) {
                    val storedValues = FileManager.valuesConfig.file.readJson() as? JsonObject ?: return@runCatching

                    storedValues.getAsJsonObject("targets")?.let {
                        valuesJson.add("targets", cloneJsonObject(it))
                    }

                    for (module in moduleManager) {
                        val jsonModule = storedValues.getAsJsonObject(module.name) ?: continue
                        valuesJson.add(module.name, cloneJsonObject(jsonModule))
                    }
                }
            }

            return TabConfigSnapshot(modulesJson, valuesJson)
        }
    }
}
