package com.rzxi.omniroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android-native OpenAI-compatible bridge. This is an initial API-key gateway
 * while the full Node.js/Next.js OmniRoute runtime is being made Android-compatible.
 */
class OmniRouteService : Service() {
    companion object {
        const val PORT = 20128
        private const val CHANNEL_ID = "omniroute_local_runtime"
        private const val NOTIFICATION_ID = 8301
        private const val TOKEN_KEY = "__local_gateway_token__"
        private const val BASE_PREFIX = "__provider_base__"
        private const val MODEL_PREFIX = "__provider_model__"
        @Volatile var isRunning: Boolean = false
            private set
    }

    private val stopping = AtomicBoolean(false)
    private val clients = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private lateinit var vault: ApiKeyVault

    override fun onCreate() {
        super.onCreate()
        vault = ApiKeyVault(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startApiServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startApiServer() {
        if (server != null) return
        stopping.set(false)
        acceptThread = Thread({
            try {
                val socketServer = ServerSocket(PORT, 32, InetAddress.getByName("127.0.0.1"))
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
        }, "omniroute-local-api").apply { isDaemon = true; start() }
    }

    private data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 10_000
                val input = BufferedInputStream(client.getInputStream())
                val first = readAsciiLine(input) ?: return
                val parts = first.split(" ")
                val method = parts.getOrNull(0)?.uppercase() ?: "GET"
                val path = parts.getOrNull(1)?.substringBefore("?") ?: "/"
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                val length = (headers["content-length"] ?: "0").toIntOrNull()?.coerceIn(0, 2_000_000) ?: 0
                val bodyBytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(bodyBytes, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val request = Request(method, path, headers, String(bodyBytes, 0, read, StandardCharsets.UTF_8))
                val response = route(request)
                val bytes = response.second.toByteArray(StandardCharsets.UTF_8)
                val statusText = when (response.first) {
                    200 -> "OK"; 201 -> "Created"; 400 -> "Bad Request"; 401 -> "Unauthorized"
                    404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Payload Too Large"
                    502 -> "Bad Gateway"; 503 -> "Service Unavailable"; else -> "Internal Server Error"
                }
                val output = BufferedOutputStream(client.getOutputStream())
                output.write(("HTTP/1.1 ${response.first} $statusText\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Authorization, Content-Type\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
                output.write(bytes)
                output.flush()
            } catch (_: Exception) {
                // Ignore disconnected clients. Never log request headers or secrets.
            }
        }
    }

    private fun route(req: Request): Pair<Int, String> {
        if (req.method == "OPTIONS") return 200 to "{}"
        if (req.path == "/health") {
            return 200 to JSONObject()
                .put("status", "ok")
                .put("runtime", "android-native-bridge")
                .put("gatewayIntegrated", false)
                .put("openAiCompatibleBridge", true)
                .toString()
        }
        if (req.path != "/v1/models" && req.path != "/v1/chat/completions") {
            return 404 to error("not_found", "Endpoint not available in Android bridge")
        }
        val expected = vault.get(TOKEN_KEY)
        if (expected.isNullOrBlank()) return 503 to error("not_configured", "Generate a local API token in OmniRoute settings first")
        val supplied = req.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (supplied.isNullOrBlank() || !constantTimeEquals(supplied, expected)) {
            return 401 to error("unauthorized", "A valid Bearer token is required")
        }
        return when {
            req.method == "GET" && req.path == "/v1/models" -> models()
            req.method == "POST" && req.path == "/v1/chat/completions" -> chat(req.body)
            req.path == "/v1/models" || req.path == "/v1/chat/completions" -> 405 to error("method_not_allowed", "Use GET /v1/models or POST /v1/chat/completions")
            else -> 404 to error("not_found", "Endpoint not found")
        }
    }

    private fun models(): Pair<Int, String> {
        val data = JSONArray()
        vault.names().filter { !it.startsWith("__") }.sorted().forEach { provider ->
            val modelName = vault.get(MODEL_PREFIX + provider)
            if (!vault.get(provider).isNullOrBlank() && !vault.get(BASE_PREFIX + provider).isNullOrBlank() && !modelName.isNullOrBlank()) {
                data.put(JSONObject().put("id", "$provider/$modelName").put("object", "model").put("owned_by", provider))
            }
        }
        return 200 to JSONObject().put("object", "list").put("data", data).toString()
    }

    private fun chat(body: String): Pair<Int, String> {
        if (body.length > 2_000_000) return 413 to error("payload_too_large", "Request body exceeds 2 MB")
        val input = try { JSONObject(body) } catch (_: Exception) {
            return 400 to error("invalid_json", "Request body must be valid JSON")
        }
        if (input.optBoolean("stream", false)) {
            return 400 to error("streaming_not_supported", "This Android bridge currently supports non-streaming chat completions only")
        }
        val requestedModel = input.optString("model", "")
        if (requestedModel.isBlank()) return 400 to error("invalid_request", "model is required")
        val provider = requestedModel.substringBefore("/")
        val model = requestedModel.substringAfter("/", "")
        if (provider.isBlank() || provider.startsWith("__") || model.isBlank()) return 400 to error("invalid_request", "Use a model ID returned by /v1/models")
        val apiKey = vault.get(provider) ?: return 400 to error("unknown_provider", "No saved credentials for the requested provider")
        val base = vault.get(BASE_PREFIX + provider)?.trimEnd('/') ?: return 400 to error("provider_not_configured", "Set this provider's OpenAI-compatible base URL")
        if (!base.startsWith("https://") && !base.startsWith("http://127.0.0.1") && !base.startsWith("http://localhost")) {
            return 400 to error("invalid_provider_url", "Provider base URL must use HTTPS")
        }
        val upstreamBody = JSONObject(input.toString()).put("model", model).toString()
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(upstreamBody.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "{}"
            if (code in 200..299) code to response
            else 502 to error("upstream_error", "Provider returned HTTP $code", JSONObject().put("upstream", safeError(response)))
        } catch (_: Exception) {
            502 to error("upstream_unreachable", "Could not reach provider. Check the base URL, internet connection, and API key.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun safeError(raw: String): String = raw.take(500).replace(Regex("(?i)(bearer\\s+)[^\\s\"']+"), "$1[redacted]")

    private fun error(code: String, message: String, extra: JSONObject? = null): String {
        val e = JSONObject().put("message", message).put("type", "omniroute_error").put("code", code)
        val body = JSONObject().put("error", e)
        if (extra != null) body.put("details", extra)
        return body.toString()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray(StandardCharsets.UTF_8)
        val y = b.toByteArray(StandardCharsets.UTF_8)
        var diff = x.size xor y.size
        for (i in 0 until minOf(x.size, y.size)) diff = diff or (x[i].toInt() xor y[i].toInt())
        return diff == 0
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val out = StringBuilder()
        while (out.length < 8192) {
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
            .setContentText("Local API listening on 127.0.0.1:$PORT")
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
