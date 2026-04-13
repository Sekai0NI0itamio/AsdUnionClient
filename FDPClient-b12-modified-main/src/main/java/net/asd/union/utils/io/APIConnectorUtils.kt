/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.io

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.asd.union.FDPClient
import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

object APIConnectorUtils {

    var discord: String = ""
        private set
    var discordApp: String = ""
        private set
    var donate: String = ""
        private set
    var changelogs: String = ""
        private set
    var bugs: String = ""
        private set

    private var appClientID: String = ""
    private var appClientSecret: String = ""

    private val picturesCache = mutableMapOf<Pair<String, String>, ResourceLocation>()
    private val cacheMutex = Mutex()

    /**
     * Data class representing an image with its metadata.
     */
    data class Picture(
        val fileName: String,
        val picType: String,
        val resourceLocation: ResourceLocation
    )

    /**
     * Asynchronously loads images and stores them in the cache.
     */
    suspend fun loadPicturesAsync() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            picturesCache.clear()
            LOGGER.info("Image cache cleared.")
        }

        try {
            val locationsUrl = "${URLRegistryUtils.PICTURES}locations.txt"
            val (locationsResponse, statusCode) = HttpUtils.get(locationsUrl)

            if (statusCode != 200) {
                throw IOException("Failed to fetch locations: HTTP $statusCode")
            }

            val details = locationsResponse.split("---")
            LOGGER.info("Image locations fetched: ${details.size} entries.")

            coroutineScope {
                details.forEach { detail ->
                    launch {
                        runCatching {
                            val (fileName, picType) = detail.split(":")
                            val imageUrl = "${URLRegistryUtils.PICTURES}$picType/$fileName.png"

                            val (imageBytes, imageStatusCode) = HttpUtils.requestStream(imageUrl, "GET")
                            if (imageStatusCode != 200) throw IOException("Failed to download image: HTTP $imageStatusCode")

                            val bufferedImage: BufferedImage = ImageIO.read(imageBytes)
                                ?: throw IOException("Failed to decode image: $imageUrl")

                             MinecraftInstance.mc.addScheduledTask {
                                try {
                                    val dynamicTexture = DynamicTexture(bufferedImage)
                                    val resourceLocation = MinecraftInstance.mc.textureManager.getDynamicTextureLocation(
                                        FDPClient.clientTitle,
                                        dynamicTexture
                                    )

                                    synchronized(picturesCache) {
                                        picturesCache[Pair(fileName, picType)] = resourceLocation
                                    }
                                    LOGGER.info("Image loaded successfully: $fileName, Type: $picType")
                                } catch (e: Exception) {
                                    LOGGER.error("Failed to create texture for image: $fileName, Type: $picType", e)
                                }
                            }
                        }.onFailure { exception ->
                            LOGGER.error("Failed to load image for detail: $detail", exception)
                        }
                    }
                }
            }

            LOGGER.info("All image load tasks scheduled successfully.")
        } catch (e: Exception) {
            LOGGER.error("Failed to load images from server.", e)
        }
    }

    /**
     * Retrieves the [ResourceLocation] for a specific image and location.
     *
     * @param image The name of the image.
     * @param location The category/location of the image.
     * @return The corresponding [ResourceLocation], or a default one if not found.
     */
    fun callImage(image: String, location: String): ResourceLocation {
        return synchronized(picturesCache) {
            picturesCache[Pair(image, location)] ?: ResourceLocation("asdunionclient/temp.png")
        }
    }

    /**
     * Asynchronously checks the API status and updates relevant properties.
     */
    suspend fun checkStatusAsync() = withContext(Dispatchers.IO) {
        try {
            val (statusResponse, statusCode) = HttpUtils.get(URLRegistryUtils.STATUS)
            if (statusCode != 200) throw IOException("Failed to fetch status: HTTP $statusCode")

            val details = statusResponse.split("///")
            require(details.size >= 6) { "Incomplete status data received." }

            appClientID = details[0]
            appClientSecret = details[1]
            discordApp = details[2]
            discord = details[4]

            LOGGER.info("API status checked successfully.")
        } catch (e: Exception) {
            LOGGER.error("Failed to verify API status.", e)
        }
    }

    /**
     * Asynchronously fetches the latest changelogs from the server.
     */
    suspend fun checkChangelogsAsync() = withContext(Dispatchers.IO) {
        // Changelog functionality disabled by user request
        // try {
        //     val (changelogsResponse, statusCode) = HttpUtils.get(URLRegistryUtils.CHANGELOGS)
        //     if (statusCode != 200) throw IOException("Failed to fetch changelogs: HTTP $statusCode")
        //
        //     changelogs = changelogsResponse
        //     LOGGER.info("Changelogs loaded successfully.")
        // } catch (e: Exception) {
        //     LOGGER.error("Failed to load changelogs.", e)
        // }
    }

    /**
     * Asynchronously fetches the latest bugs from the server.
     */
    suspend fun checkBugsAsync() = withContext(Dispatchers.IO) {
        try {
            val (bugsResponse, statusCode) = HttpUtils.get(URLRegistryUtils.BUGS)
            if (statusCode != 200) throw IOException("Failed to fetch bugs: HTTP $statusCode")

            bugs = bugsResponse
            LOGGER.info("Bugs loaded successfully.")
        } catch (e: Exception) {
            LOGGER.error("Failed to load bugs.", e)
        }
    }


    /**
     * Executes all API checks asynchronously.
     */
    suspend fun performAllChecksAsync() = coroutineScope {
        launch { checkStatusAsync() }
        // launch { checkChangelogsAsync() }  // Disabled by user request
        launch { checkBugsAsync() }
        launch { loadPicturesAsync() }
    }
}
