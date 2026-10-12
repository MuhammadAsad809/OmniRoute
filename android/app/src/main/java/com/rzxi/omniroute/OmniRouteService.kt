package com.rzxi.omniroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle proof-of-concept only.
 * This Kotlin health endpoint validates Android service lifecycle; it does NOT
 * launch Node.js or the OmniRoute gateway yet.
 */
class OmniRouteService : Service() {
    companion object {
        const val PORT = 18765
        private const val CHANNEL_ID = "omniroute_local_runtime"
        private const val NOTIFICATION_ID = 8301
        @Volatile var isRunning: Boolean = false
            private set
    }

    private val stopping = AtomicBoolean(false)
    private val clients = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startHealthServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startHealthServer() {
        if (server != null) return
        stopping.set(false)
        acceptThread = Thread({
            try {
                val socketServer = ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))
                server = socketServer
                isRunning = true
                while (!stopping.get()) {
                    val client = socketServer.accept()
                    clients.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                // Socket closure is expected when the service stops.
            } finally {
                isRunning = false
            }
        }, "omniroute-local-health").apply { isDaemon = true; start() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 3000
                val input = BufferedInputStream(client.getInputStream())
                val request = readAsciiLine(input) ?: return
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                }
                val path = request.split(" ").getOrNull(1) ?: "/"
                val found = path == "/health"
                val body = if (found)
                    """{"status":"ok","runtime":"android-scaffold","gatewayIntegrated":false}"""
                else
                    """{"error":"not_found","message":"Only /health is available in this scaffold"}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val status = if (found) "200 OK" else "404 Not Found"
                val output = BufferedOutputStream(client.getOutputStream())
                output.write(("HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                output.write(bytes)
                output.flush()
            } catch (_: Exception) {
                // Ignore malformed or disconnected local clients.
            }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder()
        while (out.length < 4096) {
            val b = input.read()
            if (b == -1) return if (out.isEmpty()) null else out.toString()
            if (b == 10) break
            if (b != 13) out.append(b.toChar())
        }
        return out.toString()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "OmniRoute local runtime", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle("OmniRoute service")
            .setContentText("Scaffold listening on 127.0.0.1:$PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopping.set(true)
        try { server?.close() } catch (_: Exception) {}
        server = null
        isRunning = false
        clients.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
