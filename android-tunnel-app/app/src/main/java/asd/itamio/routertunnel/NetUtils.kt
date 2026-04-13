package asd.itamio.routertunnel

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtils {
    fun getLocalIp(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses?.let { Collections.list(it) }.orEmpty()
            val address = addresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (address != null) {
                return address.hostAddress
            }
        }
        return null
    }
}
