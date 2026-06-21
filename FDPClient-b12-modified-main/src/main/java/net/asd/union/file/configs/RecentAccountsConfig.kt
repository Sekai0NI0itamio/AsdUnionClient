package net.asd.union.file.configs

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.asd.union.file.FileConfig
import net.asd.union.file.FileManager.PRETTY_GSON
import net.asd.union.utils.io.readJson
import java.io.File
import java.io.IOException

data class RecentAccount(
    val username: String,
    var lastServerIp: String,
    var lastJoinTimestamp: Long
)

class RecentAccountsConfig(file: File) : FileConfig(file) {

    val accounts = mutableListOf<RecentAccount>()

    companion object {
        private const val MAX_ACCOUNTS = 50
    }

    @Throws(IOException::class)
    override fun loadConfig() {
        accounts.clear()
        val root = file.readJson()
        val json = root as? JsonArray ?: return

        for (element in json) {
            val obj = element.asJsonObject
            val username = obj.get("username")?.asString ?: continue
            val lastServerIp = obj.get("lastServerIp")?.asString ?: ""
            val lastJoinTimestamp = obj.get("lastJoinTimestamp")?.asLong ?: 0L
            accounts.add(RecentAccount(username, lastServerIp, lastJoinTimestamp))
        }

        accounts.sortByDescending { it.lastJoinTimestamp }
    }

    @Throws(IOException::class)
    override fun saveConfig() {
        val jsonArray = JsonArray()
        for (account in accounts) {
            val obj = JsonObject()
            obj.addProperty("username", account.username)
            obj.addProperty("lastServerIp", account.lastServerIp)
            obj.addProperty("lastJoinTimestamp", account.lastJoinTimestamp)
            jsonArray.add(obj)
        }
        file.writeText(PRETTY_GSON.toJson(jsonArray))
    }

    fun recordJoin(username: String, serverIp: String) {
        val existing = accounts.find { it.username == username }
        if (existing != null) {
            existing.lastServerIp = serverIp
            existing.lastJoinTimestamp = System.currentTimeMillis()
            accounts.remove(existing)
            accounts.add(0, existing)
        } else {
            accounts.add(0, RecentAccount(username, serverIp, System.currentTimeMillis()))
            while (accounts.size > MAX_ACCOUNTS) {
                accounts.removeAt(accounts.size - 1)
            }
        }
    }

    fun removeAccount(username: String) {
        accounts.removeIf { it.username == username }
    }
}
