package net.asd.union.handler.network

import com.google.gson.JsonParser
import net.asd.union.event.Listenable
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.file.FileManager
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.timing.MSTimer
import java.io.InputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.Locale

object ConnectToRouter : MinecraftInstance, Listenable {
    const val TUNNEL_PORT = 25560
    private const val CMD_WIFI_CONNECT = 2
    private const val CMD_WIFI_LIST = 3
    private const val CMD_PHONE_CONNECT = 4
    private const val CMD_PHONE_DISCONNECT = 5

    private const val AUTO_DISABLE_THRESHOLD = 3
    private const val ULTRA_REFRESH_DEBOUNCE_MS = 150L

    private val interfaceRegex = Regex("\"interface\":\"([^\"]+)\"")
    private val ipRegex = Regex("\"ip\":\"([^\"]+)\"")
    private val wifiOkRegex = Regex("\"ok\":(true|false)")
    private val wifiMessageRegex = Regex("\"message\":\"([^\"]*)\"")
    private val vpnMarkers = arrayOf(
        "tun", "tap", "ppp", "wg", "utun", "ipsec", "vpn", "wireguard", "tailscale", "zerotier",
    )

    private var savedEnabled = false
    private var enabledState = false

    var enabled: Boolean
        get() = enabledState
        set(value) {
            applyEnabledState(value, persist = true, rememberPreference = true)
        }

    val persistedEnabled: Boolean
        get() = savedEnabled

    var debugEnabled = true

    var status = Status.OFF
        private set

    var lastError = ""
        private set

    var lastLocalIp = ""
        private set

    var vpnDetected = false
        private set

    var selectedInterface = ""
        private set

    var tunnelAvailable = false
        private set

    var tunnelInterface = ""
        private set

    var tunnelIp = ""
        private set

    @Volatile
    var wifiCommandInProgress = false
        private set

    @Volatile
    var wifiCommandStatusLine = ""
        private set

    @Volatile
    var wifiCommandStatusColor = 0xAAAAAA
        private set

    @Volatile
    var lastRequestedWifiSsid = ""
        private set

    @Volatile
    var wifiListInProgress = false
        private set

    @Volatile
    var wifiListStatusLine = ""
        private set

    @Volatile
    var wifiListStatusColor = 0xAAAAAA
        private set

    @Volatile
    var wifiNetworks: List<String> = emptyList()
        private set

    @Volatile
    var wifiNetworksUpdatedAtMs = 0L
        private set

    var wasAutoDisabled = false
        private set

    var autoDisableReason = ""
        private set

    private var consecutiveFailures = 0

    var lastTcpOk = false
        private set

    var lastTcpMs = -1L
        private set

    var lastTcpError = ""
        private set

    var phonePassword = ""
        private set

    private var preferredAddress: InetAddress? = null
    private val refreshTimer = MSTimer()
    private var lastUltraFastRefreshAt = 0L

    val onUpdate = handler<UpdateEvent> {
        if (!enabled) {
            return@handler
        }
    }

    fun isTunnelMode(): Boolean = enabled && status == Status.TUNNEL

    fun loadEnabledState(value: Boolean) {
        applyEnabledState(value, persist = false, rememberPreference = true, allowAutoDisable = false)
    }

    @Synchronized
    fun ultraFastRefreshServerPing(): UltraFastRefreshResult {
        val now = System.currentTimeMillis()

        if (now - lastUltraFastRefreshAt < ULTRA_REFRESH_DEBOUNCE_MS) {
            val currentIp = normalizeIp(getActivePingIp())
            logDebug("Ultra Fast Refresh skipped (debounced)")
            return UltraFastRefreshResult(currentIp, currentIp, false)
        }

        lastUltraFastRefreshAt = now

        val previousIp = normalizeIp(getActivePingIp())
        sendRefreshPacket()
        refreshStatus()
        val currentIp = normalizeIp(getActivePingIp())
        val changed = currentIp.isNotBlank() && !previousIp.equals(currentIp, ignoreCase = true)

        if (changed) {
            logDebug("Ultra Fast Refresh switched ping IP: $previousIp -> $currentIp")
        } else {
            logDebug("Ultra Fast Refresh ping IP unchanged: ${currentIp.ifEmpty { "unknown" }}")
        }

        return UltraFastRefreshResult(previousIp, currentIp, changed)
    }

    fun getActivePingIp(): String {
        val fromTunnel = normalizeIp(tunnelIp)
        if (tunnelAvailable && fromTunnel.isNotBlank()) {
            return fromTunnel
        }

        val fromInterface = normalizeIp(lastLocalIp)
        return if (status == Status.CONNECTED && fromInterface.isNotBlank()) {
            fromInterface
        } else {
            normalizeIp(detectSystemOutboundIp())
        }
    }

    fun getPreferredLocalAddressFor(remoteAddress: InetAddress?): InetAddress? {
        if (!enabled || status != Status.CONNECTED || remoteAddress == null) {
            return null
        }

        if (
            remoteAddress.isLoopbackAddress ||
            remoteAddress.isLinkLocalAddress ||
            remoteAddress.isMulticastAddress ||
            remoteAddress.isSiteLocalAddress
        ) {
            return null
        }

        val localAddress = preferredAddress ?: return null
        if (localAddress is Inet4Address != (remoteAddress is Inet4Address)) {
            return null
        }

        val localInterface = NetworkInterface.getByInetAddress(localAddress) ?: return null
        if (!localInterface.isUp || localInterface.isLoopback || !lastTcpOk) {
            return null
        }

        return localAddress
    }

    val statusLine: String
        get() = when {
            !enabled && wasAutoDisabled -> "Auto-disabled: $autoDisableReason"
            !enabled -> "Off"
            status == Status.TUNNEL -> "Tunnel active: $tunnelInterface (${tunnelIp.ifEmpty { "?" }})"
            status == Status.CONNECTED -> "Interface bind: ${lastLocalIp.ifEmpty { "?" }}"
            status == Status.FAILED -> lastError.ifEmpty { "Failed" }
            else -> "Detecting..."
        }

    val statusColor: Int
        get() = when {
            !enabled && wasAutoDisabled -> 0xFF8A40
            !enabled -> 0xAAAAAA
            status == Status.TUNNEL -> 0x55FF55
            status == Status.CONNECTED -> 0xAAFF55
            status == Status.FAILED -> 0xFF5555
            else -> 0xFFFF55
        }

    fun refreshStatus(allowAutoDisable: Boolean = true) {
        logDebug("refresh start")
        status = Status.DETECTING
        refreshTimer.reset()

        val tunnel = checkTunnel()
        tunnelAvailable = tunnel.available
        tunnelInterface = tunnel.iface
        tunnelIp = tunnel.ip

        if (tunnel.available) {
            status = Status.TUNNEL
            lastError = ""
            consecutiveFailures = 0
            logDebug("Tunnel available on port $TUNNEL_PORT - ${tunnel.iface} (${tunnel.ip})")
            return
        }

        logDebug("Tunnel not running on port $TUNNEL_PORT")
        if (debugEnabled) {
            dumpInterfaces()
        }

        val addressResult = findPreferredAddress()
        val addressError = addressResult.error
        if (addressError != null) {
            onDetectionFailed(addressError, allowAutoDisable)
            return
        }

        preferredAddress = addressResult.address
        lastLocalIp = addressResult.address?.hostAddress.orEmpty()

        val tcpResult = tcpProbe("bing.com", 443, addressResult.address)
        lastTcpOk = tcpResult.ok
        lastTcpMs = tcpResult.timeMs
        lastTcpError = tcpResult.error.orEmpty()

        if (tcpResult.ok) {
            status = Status.CONNECTED
            lastError = ""
            consecutiveFailures = 0
            logDebug("Interface binding OK - $selectedInterface ($lastLocalIp) tcp=${tcpResult.timeMs}ms")
            return
        }

        onDetectionFailed("No route via $selectedInterface", allowAutoDisable)
    }

    fun sendRefreshPacket() {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 1000)
                socket.soTimeout = 2000
                socket.getOutputStream().write(1)
                socket.getOutputStream().flush()

                val input = socket.getInputStream()
                val json = readFramedPayload(input)
                if (json.isNotBlank()) {
                    logDebug("Tunnel refresh response: $json")
                }
            }
        }.onFailure {
            logDebug("Tunnel refresh failed (tunnel may not be running): ${it.message}")
        }
    }

    data class WifiConnectResult(
        val ok: Boolean,
        val message: String,
        val rawJson: String,
    )

    data class WifiListResult(
        val ok: Boolean,
        val networks: List<String>,
        val message: String,
        val rawJson: String,
    )

    data class PhoneConnectResult(
        val ok: Boolean,
        val message: String,
        val rawJson: String,
    )

    fun setRequestedWifiSsid(ssid: String) {
        lastRequestedWifiSsid = ssid.trim()
    }

    fun connectWifiThroughTunnel(ssid: String) {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) {
            wifiCommandStatusLine = "Wi-Fi: SSID is empty"
            wifiCommandStatusColor = 0xFF5555
            return
        }

        if (!isMacOs()) {
            wifiCommandStatusLine = "Wi-Fi: only supported on macOS"
            wifiCommandStatusColor = 0xFF5555
            return
        }

        if (wifiCommandInProgress) {
            wifiCommandStatusLine = "Wi-Fi: already connecting…"
            wifiCommandStatusColor = 0xFFFF55
            return
        }

        lastRequestedWifiSsid = trimmed
        wifiCommandInProgress = true
        wifiCommandStatusLine = "Wi-Fi: connecting to $trimmed…"
        wifiCommandStatusColor = 0xFFFF55

        Thread {
            try {
                val result = requestWifiConnect(trimmed)
                wifiCommandStatusLine = if (result.ok) {
                    if (result.message.isNotBlank()) {
                        "Wi-Fi: ${result.message}"
                    } else {
                        "Wi-Fi: connected to $trimmed"
                    }
                } else {
                    "Wi-Fi: ${result.message.ifBlank { "Failed" }}"
                }
                wifiCommandStatusColor = if (result.ok) 0x55FF55 else 0xFF5555

                Thread.sleep(1500)
                sendRefreshPacket()
                refreshStatus()
            } catch (throwable: Throwable) {
                wifiCommandStatusLine = "Wi-Fi: ${throwable.message ?: throwable::class.java.simpleName}"
                wifiCommandStatusColor = 0xFF5555
            } finally {
                wifiCommandInProgress = false
            }
        }.apply { isDaemon = true }.start()
    }

    fun refreshWifiNetworksThroughTunnel() {
        if (!isMacOs()) {
            wifiListStatusLine = "Device scan: only supported on macOS"
            wifiListStatusColor = 0xFF5555
            return
        }

        if (wifiListInProgress) {
            wifiListStatusLine = "Device scan: already loading…"
            wifiListStatusColor = 0xFFFF55
            return
        }

        wifiListInProgress = true
        wifiListStatusLine = "Device scan: loading…"
        wifiListStatusColor = 0xFFFF55

        Thread {
            try {
                val result = requestWifiList()
                wifiNetworks = result.networks
                wifiNetworksUpdatedAtMs = System.currentTimeMillis()
                wifiListStatusLine = if (result.ok) {
                    if (result.message.isNotBlank()) {
                        "Device scan: ${result.message}"
                    } else {
                        "Device scan: ${result.networks.size} devices"
                    }
                } else {
                    "Device scan: ${result.message.ifBlank { "Failed" }}"
                }
                wifiListStatusColor = if (result.ok) 0x55FF55 else 0xFF5555
            } catch (throwable: Throwable) {
                wifiListStatusLine = "Device scan: ${throwable.message ?: throwable::class.java.simpleName}"
                wifiListStatusColor = 0xFF5555
            } finally {
                wifiListInProgress = false
            }
        }.apply { isDaemon = true }.start()
    }

    fun setPhonePassword(value: String, persist: Boolean = true) {
        phonePassword = value.trim()
        if (persist) {
            persistState()
        }
    }

    fun writePhonePasswordFile(): Boolean {
        if (phonePassword.isBlank()) return false
        val file = java.io.File(FileManager.dir, "router_tunnel.password")
        return runCatching {
            FileManager.writeFile(file, phonePassword)
            true
        }.getOrDefault(false)
    }

    fun requestWifiConnect(ssid: String): WifiConnectResult {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) {
            return WifiConnectResult(ok = false, message = "SSID is empty", rawJson = "")
        }

        val payload = trimmed.toByteArray(Charsets.UTF_8)
        if (payload.isEmpty() || payload.size > 250) {
            return WifiConnectResult(ok = false, message = "SSID is too long", rawJson = "")
        }

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 1000)
                socket.soTimeout = 120_000

                val output = socket.getOutputStream()
                output.write(CMD_WIFI_CONNECT)
                output.write(payload.size)
                output.write(payload)
                output.flush()

                val input = socket.getInputStream()
                val json = readFramedPayload(input)
                if (json.isBlank()) {
                    WifiConnectResult(ok = false, message = "No response from tunnel", rawJson = "")
                } else {
                    logDebug("Tunnel Wi-Fi response: $json")

                    val ok = wifiOkRegex.find(json)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) == true
                    val message = wifiMessageRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
                    WifiConnectResult(ok = ok, message = message, rawJson = json)
                }
            }
        }.getOrElse {
            WifiConnectResult(ok = false, message = it.message ?: it::class.java.simpleName, rawJson = "")
        }
    }

    fun requestWifiList(): WifiListResult {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 1000)
                socket.soTimeout = 30_000

                val output = socket.getOutputStream()
                output.write(CMD_WIFI_LIST)
                output.flush()

                val input = socket.getInputStream()
                val json = readFramedPayload(input)
                if (json.isBlank()) {
                    WifiListResult(ok = false, networks = emptyList(), message = "No response from tunnel", rawJson = "")
                } else {
                    logDebug("Tunnel device scan response: $json")
                    parseWifiListJson(json)
                }
            }
        }.getOrElse {
            WifiListResult(ok = false, networks = emptyList(), message = it.message ?: it::class.java.simpleName, rawJson = "")
        }
    }

    fun requestPhoneConnect(host: String, port: Int, password: String): PhoneConnectResult {
        val safeHost = host.trim()
        val safePassword = password.trim()
        val safePort = if (port in 1..65535) port else 45454

        if (safeHost.isEmpty()) {
            return PhoneConnectResult(false, "Host is empty", "")
        }
        if (safePassword.isEmpty()) {
            return PhoneConnectResult(false, "Password is empty", "")
        }

        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 1000)
                socket.soTimeout = 10_000

                val hostBytes = safeHost.toByteArray(Charsets.UTF_8)
                val passBytes = safePassword.toByteArray(Charsets.UTF_8)
                if (hostBytes.isEmpty() || hostBytes.size > 250) {
                    return@use PhoneConnectResult(false, "Host too long", "")
                }
                if (passBytes.isEmpty() || passBytes.size > 250) {
                    return@use PhoneConnectResult(false, "Password too long", "")
                }

                val output = socket.getOutputStream()
                output.write(CMD_PHONE_CONNECT)
                output.write(hostBytes.size)
                output.write(hostBytes)
                output.write((safePort shr 8) and 0xFF)
                output.write(safePort and 0xFF)
                output.write(passBytes.size)
                output.write(passBytes)
                output.flush()

                val json = readFramedPayload(socket.getInputStream())
                if (json.isBlank()) {
                    PhoneConnectResult(false, "No response from tunnel", "")
                } else {
                    val ok = wifiOkRegex.find(json)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) == true
                    val message = wifiMessageRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
                    PhoneConnectResult(ok, message, json)
                }
            }
        }.getOrElse {
            PhoneConnectResult(false, it.message ?: it::class.java.simpleName, "")
        }
    }

    fun requestPhoneDisconnect(): PhoneConnectResult {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 1000)
                socket.soTimeout = 2000
                socket.getOutputStream().write(CMD_PHONE_DISCONNECT)
                socket.getOutputStream().flush()

                val json = readFramedPayload(socket.getInputStream())
                if (json.isBlank()) {
                    PhoneConnectResult(false, "No response from tunnel", "")
                } else {
                    val ok = wifiOkRegex.find(json)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) == true
                    val message = wifiMessageRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
                    PhoneConnectResult(ok, message, json)
                }
            }
        }.getOrElse {
            PhoneConnectResult(false, it.message ?: it::class.java.simpleName, "")
        }
    }

    private fun parseWifiListJson(json: String): WifiListResult {
        return runCatching {
            val obj = JsonParser().parse(json).asJsonObject
            val ok = obj.get("ok")?.asBoolean ?: false
            val message = obj.get("message")?.asString.orEmpty()
            val networks = obj.getAsJsonArray("networks")
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                .orEmpty()

            WifiListResult(ok = ok, networks = networks, message = message, rawJson = json)
        }.getOrElse {
            WifiListResult(ok = false, networks = emptyList(), message = it.message ?: "Bad JSON", rawJson = json)
        }
    }

    private fun checkTunnel(): TunnelCheckResult {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TUNNEL_PORT), 500)
                socket.soTimeout = 500
                socket.getOutputStream().write(0)
                socket.getOutputStream().flush()

                val input = socket.getInputStream()
                val json = readFramedPayload(input)
                if (json.isBlank()) {
                    TunnelCheckResult(false, "", "")
                } else {
                    logDebug("Tunnel health response: $json")
                    val iface = interfaceRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
                    val ip = ipRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
                    TunnelCheckResult(true, iface, ip)
                }
            }
        }.getOrElse {
            logDebug("Tunnel check failed: ${it.message}")
            TunnelCheckResult(false, "", "")
        }
    }

    private fun isMacOs(): Boolean {
        val os = System.getProperty("os.name") ?: return false
        return os.lowercase(Locale.ENGLISH).contains("mac")
    }

    private fun onDetectionFailed(reason: String, allowAutoDisable: Boolean = true) {
        status = Status.FAILED
        lastError = reason
        if (!allowAutoDisable) {
            consecutiveFailures = 0
            logDebug("Detection failed (startup load): $reason")
            return
        }

        consecutiveFailures += 1
        logDebug("Detection failed ($consecutiveFailures/$AUTO_DISABLE_THRESHOLD): $reason")

        if (consecutiveFailures >= AUTO_DISABLE_THRESHOLD) {
            logDebug("Auto-disabling after $consecutiveFailures consecutive failures")
            wasAutoDisabled = true
            autoDisableReason = reason
            applyEnabledState(false, persist = false, rememberPreference = false, allowAutoDisable = true)
        }
    }

    private fun applyEnabledState(
        value: Boolean,
        persist: Boolean,
        rememberPreference: Boolean,
        allowAutoDisable: Boolean = true,
    ) {
        val stateChanged = enabledState != value

        if (rememberPreference) {
            savedEnabled = value
        }

        if (!stateChanged) {
            if (persist) {
                persistState()
            }
            return
        }

        enabledState = value

        if (value) {
            consecutiveFailures = 0
            wasAutoDisabled = false
            autoDisableReason = ""
            sendRefreshPacket()
            refreshStatus(allowAutoDisable)
        } else {
            status = Status.OFF
            lastError = ""
            lastLocalIp = ""
            vpnDetected = false
            selectedInterface = ""
            preferredAddress = null
            tunnelAvailable = false
            tunnelInterface = ""
            tunnelIp = ""
            consecutiveFailures = 0
            refreshTimer.zero()
        }

        if (persist) {
            persistState()
        }
    }

    private fun persistState() {
        runCatching {
            FileManager.saveConfig(FileManager.valuesConfig)
        }.onFailure {
            logDebug("Failed to persist router state: ${it.message}")
        }
    }

    private fun findPreferredAddress(): AddressResult {
        vpnDetected = false
        selectedInterface = ""

        val interfaces = NetworkInterface.getNetworkInterfaces()?.let(Collections::list)
            ?: return AddressResult(null, "No network interfaces")

        if (interfaces.isEmpty()) {
            return AddressResult(null, "No network interfaces")
        }

        val candidates = mutableListOf<Pair<NetworkInterface, InetAddress>>()

        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }

            val displayName = networkInterface.displayName ?: networkInterface.name
            val nameLower = displayName.lowercase(Locale.ENGLISH)
            val isVpn = isVpnInterface(networkInterface, nameLower)

            if (isVpn) {
                vpnDetected = true
            }

            val ipv4 = Collections.list(networkInterface.inetAddresses)
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }

            if (ipv4 != null && !isVpn) {
                candidates += networkInterface to ipv4
            }
        }

        candidates.firstOrNull()?.let { (networkInterface, address) ->
            selectedInterface = networkInterface.displayName ?: networkInterface.name
            return AddressResult(address, null)
        }

        if (vpnDetected) {
            return AddressResult(null, "No non-VPN interface")
        }

        val fallback = interfaces.asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .asSequence()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .map { networkInterface to it }
            }
            .firstOrNull()

        fallback?.let { (networkInterface, address) ->
            selectedInterface = networkInterface.displayName ?: networkInterface.name
            return AddressResult(address, null)
        }

        return AddressResult(null, "No usable address")
    }

    private fun isVpnInterface(networkInterface: NetworkInterface, nameLower: String): Boolean {
        if (networkInterface.isPointToPoint) {
            return true
        }

        return vpnMarkers.any { marker ->
            nameLower.contains(marker)
        }
    }

    private fun tcpProbe(host: String, port: Int, localAddress: InetAddress?): ProbeResult {
        val start = System.currentTimeMillis()
        val socket = Socket()

        return try {
            if (localAddress != null) {
                socket.bind(InetSocketAddress(localAddress, 0))
            }

            socket.connect(InetSocketAddress(host, port), 1500)
            ProbeResult(true, System.currentTimeMillis() - start, "")
        } catch (throwable: Throwable) {
            ProbeResult(
                false,
                System.currentTimeMillis() - start,
                throwable.message ?: throwable::class.java.simpleName,
            )
        } finally {
            runCatching {
                socket.close()
            }
        }
    }

    private fun detectSystemOutboundIp(): String {
        val outbound = runCatching {
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53))
                val local = socket.localAddress
                if (local is Inet4Address && !local.isLoopbackAddress) {
                    local.hostAddress
                } else {
                    ""
                }
            }
        }.getOrDefault("")

        if (normalizeIp(outbound).isNotBlank()) {
            return outbound
        }

        val interfaces = NetworkInterface.getNetworkInterfaces()?.let(Collections::list).orEmpty()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }

            val address = Collections.list(networkInterface.inetAddresses)
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?: continue

            return address.hostAddress
        }

        return ""
    }

    private fun normalizeIp(ip: String): String {
        val trimmed = ip.trim()
        return if (trimmed.isEmpty() || trimmed == "0.0.0.0") {
            ""
        } else {
            trimmed
        }
    }

    private fun dumpInterfaces() {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.let(Collections::list).orEmpty()

        for (networkInterface in interfaces) {
            val name = networkInterface.displayName ?: networkInterface.name
            val flags = buildList {
                if (networkInterface.isUp) add("up")
                if (networkInterface.isLoopback) add("lo")
                if (networkInterface.isPointToPoint) add("p2p")
            }

            logDebug("  iface $name (${networkInterface.name}) ${flags.joinToString(",")} mtu=${networkInterface.mtu}")

            for (address in Collections.list(networkInterface.inetAddresses)) {
                val version = if (address is Inet4Address) "v4" else "v6"
                logDebug("    $version: ${address.hostAddress}")
            }
        }
    }

    private fun logDebug(message: String) {
        if (debugEnabled) {
            ClientUtils.LOGGER.info("[ConnectToRouter] $message")
        }
    }

    private fun readFramedPayload(input: InputStream): String {
        val first = input.read()
        if (first <= 0) {
            return ""
        }

        return if (first == 0xFF) {
            val hi = input.read()
            val lo = input.read()
            if (hi < 0 || lo < 0) {
                ""
            } else {
                val length = (hi shl 8) or lo
                if (length <= 0) "" else readSizedPayload(input, length)
            }
        } else {
            readSizedPayload(input, first)
        }
    }

    private fun readSizedPayload(input: InputStream, length: Int): String {
        val data = ByteArray(length)
        var read = 0

        while (read < length) {
            val count = input.read(data, read, length - read)
            if (count <= 0) {
                break
            }
            read += count
        }

        return String(data, 0, read, Charsets.UTF_8)
    }

    enum class Status {
        OFF,
        DETECTING,
        TUNNEL,
        CONNECTED,
        FAILED,
    }

    data class UltraFastRefreshResult(
        val previousIp: String,
        val currentIp: String,
        val changed: Boolean,
    )

    private data class TunnelCheckResult(
        val available: Boolean,
        val iface: String,
        val ip: String,
    )

    private data class AddressResult(
        val address: InetAddress?,
        val error: String?,
    )

    private data class ProbeResult(
        val ok: Boolean,
        val timeMs: Long,
        val error: String?,
    )
}
