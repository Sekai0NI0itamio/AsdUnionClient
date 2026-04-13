package asd.itamio.routertunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TunnelService : Service() {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        if (running.get()) return

        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val port = prefs.getInt(MainActivity.KEY_PORT, MainActivity.DEFAULT_PORT)

        running.set(true)
        isRunning = true
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Running on port $port"))

        thread(isDaemon = true, name = "TunnelServer") {
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress("0.0.0.0", port))
                    serverSocket = server

                    while (running.get()) {
                        val client = server.accept()
                        executor.execute { handleClient(client) }
                    }
                }
            } catch (e: Exception) {
                updateNotification("Server error: ${e.message}")
                stopServer()
            }
        }
    }

    private fun stopServer() {
        if (!running.getAndSet(false)) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 4000
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val line = readLine(input) ?: return

                if (line.startsWith("PING")) {
                    val label = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .getString(MainActivity.KEY_NAME, "My Internet").orEmpty()
                    writeLine(output, "PONG $label")
                    return
                }

                if (!line.startsWith("AUTH ")) {
                    writeLine(output, "ERROR AUTH")
                    return
                }

                val password = line.removePrefix("AUTH ").trim()
                val expected = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    .getString(MainActivity.KEY_PASSWORD, "").orEmpty()

                if (expected.isBlank() || password != expected) {
                    writeLine(output, "ERROR AUTH")
                    return
                }

                val token = randomToken()
                writeLine(output, "OK $token")

                val connectLine = readLine(input) ?: return
                if (!connectLine.startsWith("CONNECT ")) {
                    writeLine(output, "ERROR CONNECT")
                    return
                }

                val parts = connectLine.split(" ")
                if (parts.size < 4) {
                    writeLine(output, "ERROR CONNECT")
                    return
                }

                val host = parts[1].trim()
                val port = parts[2].toIntOrNull() ?: 0
                val tokenCheck = parts[3].trim()
                if (tokenCheck != token || port !in 1..65535 || host.isBlank()) {
                    writeLine(output, "ERROR CONNECT")
                    return
                }

                val remote = Socket()
                remote.connect(InetSocketAddress(host, port), 5000)
                writeLine(output, "CONNECT OK")

                socket.soTimeout = 0
                remote.soTimeout = 0

                relay(socket.getInputStream(), remote.getOutputStream(), remote)
                relay(remote.getInputStream(), socket.getOutputStream(), socket)
            } catch (_: Exception) {
            }
        }
    }

    private fun relay(input: InputStream, output: OutputStream, closeSocket: Socket) {
        thread(isDaemon = true) {
            try {
                val buffer = ByteArray(65536)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    output.flush()
                }
            } catch (_: Exception) {
            } finally {
                try {
                    closeSocket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) break
            val c = b.toChar()
            if (c == '\n') break
            if (c != '\r') sb.append(c)
            if (sb.length > 1024) break
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun writeLine(output: OutputStream, value: String) {
        val data = (value + "\n").toByteArray(Charsets.UTF_8)
        output.write(data)
        output.flush()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun buildNotification(text: String): Notification {
        val channelId = ensureChannel()
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Itamio Router Tunnel")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun ensureChannel(): String {
        val channelId = "router_tunnel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Itamio Router Tunnel",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RouterTunnel:WakeLock")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "asd.itamio.routertunnel.START"
        const val ACTION_STOP = "asd.itamio.routertunnel.STOP"
        const val NOTIFICATION_ID = 2001
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
