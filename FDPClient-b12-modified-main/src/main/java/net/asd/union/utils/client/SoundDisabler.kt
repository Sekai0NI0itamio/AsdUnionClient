package net.asd.union.utils.client

import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.MinecraftInstance
import java.io.File

object SoundDisabler : MinecraftInstance {

    fun disableSoundForAppleSilicon() {
        if (!isAppleSilicon()) {
            return
        }

        try {
            val optionsFile = File(mc.mcDataDir, "options.txt")
            if (!optionsFile.exists()) {
                LOGGER.info("[SoundDisabler] options.txt not found, skipping sound disable")
                return
            }

            val content = optionsFile.readText()
            val modified = disableSoundVolumes(content)

            if (modified != content) {
                optionsFile.writeText(modified)
                LOGGER.info("[SoundDisabler] Successfully disabled sound volumes for Apple Silicon compatibility")
            } else {
                LOGGER.info("[SoundDisabler] Sound volumes already disabled or not found in options.txt")
            }
        } catch (e: Exception) {
            LOGGER.error("[SoundDisabler] Failed to disable sound", e)
        }
    }

    private fun disableSoundVolumes(content: String): String {
        var result = content

        // Match and replace soundVolume:VALUE
        val soundVolumeRegex = Regex("soundVolume:([-+]?\\d*\\.?\\d+)")
        result = soundVolumeRegex.replace(result) { match ->
            val currentValue = match.groupValues[1].toDoubleOrNull() ?: 1.0
            if (currentValue > 0.0) {
                LOGGER.info("[SoundDisabler] Found soundVolume: $currentValue, setting to 0.0")
                "soundVolume:0.0"
            } else {
                match.value
            }
        }

        // Match and replace musicVolume:VALUE
        val musicVolumeRegex = Regex("musicVolume:([-+]?\\d*\\.?\\d+)")
        result = musicVolumeRegex.replace(result) { match ->
            val currentValue = match.groupValues[1].toDoubleOrNull() ?: 1.0
            if (currentValue > 0.0) {
                LOGGER.info("[SoundDisabler] Found musicVolume: $currentValue, setting to 0.0")
                "musicVolume:0.0"
            } else {
                match.value
            }
        }

        return result
    }

    private fun isAppleSilicon(): Boolean {
        val osArch = System.getProperty("os.arch")
        val osName = System.getProperty("os.name").lowercase()
        return osName.contains("mac") && (osArch == "aarch64" || osArch == "arm64")
    }
}
