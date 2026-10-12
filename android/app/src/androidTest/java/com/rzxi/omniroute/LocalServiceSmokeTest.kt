package com.rzxi.omniroute

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class LocalServiceSmokeTest {
    @Test
    fun foregroundServiceServesLoopbackHealthEndpoint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startForegroundService(Intent(context, OmniRouteService::class.java))

        var lastError: Exception? = null
        var body: String? = null
        val deadline = System.currentTimeMillis() + 15_000L

        while (System.currentTimeMillis() < deadline && body == null) {
            try {
                val connection = URL("http://127.0.0.1:${OmniRouteService.PORT}/health")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 500
                connection.readTimeout = 1000
                connection.requestMethod = "GET"
                try {
                    if (connection.responseCode == 200) {
                        body = connection.inputStream.bufferedReader().use { it.readText() }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                lastError = error
                Thread.sleep(250)
            }
        }

        try {
            assertTrue(
                "Health endpoint did not respond. Last error: ${lastError?.message}",
                body != null
            )
            assertTrue("Unexpected runtime marker: $body", body!!.contains("\"runtime\":\"android-scaffold\""))
            assertTrue("Scaffold must not claim gateway integration: $body", body!!.contains("\"gatewayIntegrated\":false"))
        } finally {
            context.stopService(Intent(context, OmniRouteService::class.java))
        }
    }
}
