/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.file.configs

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import me.liuli.elixir.account.CrackedAccount
import me.liuli.elixir.account.MinecraftAccount
import me.liuli.elixir.account.MojangAccount
import me.liuli.elixir.manage.AccountSerializer.fromJson
import me.liuli.elixir.manage.AccountSerializer.toJson
import net.asd.union.file.FileConfig
import net.asd.union.file.FileManager.PRETTY_GSON
import net.asd.union.utils.io.readJson
import java.io.*

class AccountsConfig(file: File) : FileConfig(file) {

    val accounts = mutableListOf<MinecraftAccount>()

    /**
     * When enabled, account creation will attempt to resolve the username's real
     * Mojang UUID so the session presents a proper UUID to servers.
     */
    var properOfflineAccounts: Boolean = false

    /**
     * Load config from file
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun loadConfig() {
        clearAccounts()
        val root = file.readJson()

        // Support both legacy bare-array format and new object format
        val json: JsonArray
        if (root is JsonObject) {
            properOfflineAccounts = root.get("properOfflineAccounts")?.asBoolean ?: false
            json = root.getAsJsonArray("accounts") ?: return
        } else {
            json = root as? JsonArray ?: return
        }

        for (accountElement in json) {
            val accountObject = accountElement.asJsonObject
            try {
                // Import Elixir account format
                accounts += fromJson(accountElement.asJsonObject)
            } catch (e: JsonSyntaxException) {
                // Import old account format
                val name = accountObject["name"]
                val password = accountObject["password"]
                val inGameName = accountObject["inGameName"]
                if (inGameName.isJsonNull && password.isJsonNull) {
                    val mojangAccount = MojangAccount()
                    mojangAccount.email = name.asString
                    mojangAccount.name = inGameName.asString
                    mojangAccount.password = password.asString
                    accounts += mojangAccount
                } else {
                    val crackedAccount = CrackedAccount()
                    crackedAccount.name = name.asString
                    accounts += crackedAccount
                }
            } catch (e: IllegalStateException) {
                val name = accountObject["name"]
                val password = accountObject["password"]
                val inGameName = accountObject["inGameName"]
                if (inGameName.isJsonNull && password.isJsonNull) {
                    val mojangAccount = MojangAccount()
                    mojangAccount.email = name.asString
                    mojangAccount.name = inGameName.asString
                    mojangAccount.password = password.asString
                    accounts += mojangAccount
                } else {
                    val crackedAccount = CrackedAccount()
                    crackedAccount.name = name.asString
                    accounts += crackedAccount
                }
            }
        }
    }

    /**
     * Save config to file
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun saveConfig() {
        val jsonArray = JsonArray()

        for (minecraftAccount in accounts)
            jsonArray.add(toJson(minecraftAccount))

        val root = JsonObject()
        root.addProperty("properOfflineAccounts", properOfflineAccounts)
        root.add("accounts", jsonArray)

        file.writeText(PRETTY_GSON.toJson(root))
    }

    /**
     * Add cracked account to config
     *
     * @param name of account
     */
    fun addCrackedAccount(name: String) {
        val crackedAccount = CrackedAccount()
        crackedAccount.name = name

        if (!accountExists(crackedAccount)) accounts += crackedAccount
    }

    /**
     * Add account to config
     *
     * @param name     of account
     * @param password of password
     */
    fun addMojangAccount(name: String, password: String) {
        val mojangAccount = MojangAccount()
        mojangAccount.name = name
        mojangAccount.password = password

        if (!accountExists(mojangAccount)) accounts += mojangAccount
    }

    /**
     * Add account to config
     */
    fun addAccount(account: MinecraftAccount) = accounts.add(account)

    /**
     * Remove account from config
     *
     * @param selectedSlot of the account
     */
    fun removeAccount(selectedSlot: Int) = accounts.removeAt(selectedSlot)

    /**
     * Removed an account from the config
     *
     * @param account the account
     */
    fun removeAccount(account: MinecraftAccount) = accounts.remove(account)

    /**
     * Check if the account is already added
     */
    fun accountExists(newAccount: MinecraftAccount) =
        accounts.any { it::class == newAccount::class && it.name == newAccount.name }

    /**
     * Clear all minecraft accounts from alt array
     */
    fun clearAccounts() = accounts.clear()
}