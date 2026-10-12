package com.rzxi.omniroute

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var vault: ApiKeyVault
    private val bg = Color.rgb(12, 16, 27)
    private val panel = Color.rgb(24, 31, 47)
    private val accent = Color.rgb(116, 132, 255)
    private val muted = Color.rgb(165, 176, 198)
    private val mainText = Color.rgb(244, 247, 255)
    private val providerBasePrefix = "__provider_base__"
    private val providerModelPrefix = "__provider_model__"
    private var selectedPage = "Home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = ApiKeyVault(this)
        requestNotificationPermissionIfNeeded()
        buildShell()
        showPage("Home")
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(18))
        }
        header.addView(label("OMNIROUTE", 12f, accent, true))
        header.addView(label("AI Gateway", 27f, mainText, true))
        header.addView(label("On-device control center", 13f, muted))
        root.addView(header)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Home", "Providers", "API", "Settings").forEach { page ->
            val b = Button(this).apply {
                text = page
                textSize = 11f
                isAllCaps = false
                setOnClickListener { showPage(page) }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(4)
            })
        }
        root.addView(nav)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showPage(page: String) {
        selectedPage = page
        content.removeAllViews()
        when (page) {
            "Home" -> homePage()
            "Providers" -> providersPage()
            "API" -> apiPage()
            else -> settingsPage()
        }
    }

    private fun homePage() {
        content.addView(label("Welcome back", 23f, mainText, true))
        content.addView(label("Manage your local gateway setup from one place.", 14f, muted))
        content.addView(space(18))
        card {
            addView(label("RUNTIME STATUS", 11f, muted, true))
            addView(label("UI preview mode", 21f, mainText, true))
            addView(label("The Android shell is being built first. The real Node.js gateway is not connected yet.", 13f, muted))
            addView(space(10))
            addView(actionButton("Start lifecycle test service") {
                val intent = Intent(this@MainActivity, OmniRouteService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                Toast.makeText(this@MainActivity, "Starting test service", Toast.LENGTH_SHORT).show()
            })
            addView(actionButton("Stop service") {
                stopService(Intent(this@MainActivity, OmniRouteService::class.java))
                Toast.makeText(this@MainActivity, "Service stopped", Toast.LENGTH_SHORT).show()
            })
        }
        content.addView(space(14))
        card {
            addView(label("QUICK OVERVIEW", 11f, muted, true))
            addView(label("${vault.names().count { !it.startsWith("__") }} provider credential(s) saved", 18f, mainText, true))
            addView(label("Credentials are encrypted with Android Keystore. Provider requests are not active until the gateway runtime is integrated.", 13f, muted))
            addView(actionButton("Manage providers") { showPage("Providers") })
        }
        content.addView(space(14))
        card {
            addView(label("LOCAL API", 11f, muted, true))
            addView(label("http://127.0.0.1:20128/v1", 16f, mainText, true))
            addView(label("Planned gateway address. It is not served by this UI scaffold yet.", 13f, muted))
            addView(actionButton("API settings") { showPage("API") })
        }
    }

    private fun providersPage() {
        content.addView(label("Providers", 23f, mainText, true))
        content.addView(label("Store provider credentials locally. Keys are encrypted and never displayed after saving.", 14f, muted))
        content.addView(space(12))
        content.addView(actionButton("＋ Add provider API key") { addProviderDialog() })
        content.addView(space(12))
        val names = vault.names().filter { !it.startsWith("__") }.sorted()
        if (names.isEmpty()) {
            card {
                addView(label("No providers configured", 17f, mainText, true))
                addView(label("Add a provider name and API key to prepare your local configuration.", 13f, muted))
            }
        } else {
            names.forEach { name ->
                card {
                    addView(label(name, 17f, mainText, true))
                    addView(label("API key saved · encrypted on this device", 12f, muted))
                    addView(actionButton("Remove credential") {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Remove $name?")
                            .setMessage("This deletes the saved credential from this app.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove") { _, _ ->
                                vault.remove(name)
                                vault.remove(providerBasePrefix + name)
                                vault.remove(providerModelPrefix + name)
                                showPage("Providers")
                            }.show()
                    })
                }
                content.addView(space(10))
            }
        }
        content.addView(space(12))
        content.addView(label("Note: saving a key does not send a request yet. The OmniRoute gateway must be integrated before provider testing and model discovery can work.", 12f, muted))
    }

    private fun addProviderDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val provider = EditText(this).apply {
            hint = "Provider name (e.g. OpenAI, Gemini, OpenRouter)"
            setSingleLine(true)
        }
        val baseUrl = EditText(this).apply {
            hint = "OpenAI-compatible base URL (e.g. https://api.openai.com)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val modelName = EditText(this).apply {
            hint = "Model ID (e.g. gpt-4o-mini)"
            setSingleLine(true)
        }
        val key = EditText(this).apply {
            hint = "Provider API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(provider)
        box.addView(baseUrl)
        box.addView(modelName)
        box.addView(key)
        AlertDialog.Builder(this)
            .setTitle("Add provider")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = provider.text.toString().trim()
                val secret = key.text.toString().trim()
                val endpoint = baseUrl.text.toString().trim().trimEnd('/')
                val model = modelName.text.toString().trim()
                if (name.isBlank() || secret.isBlank() || endpoint.isBlank() || model.isBlank()) {
                    Toast.makeText(this, "Enter provider name, base URL, model ID, and API key", Toast.LENGTH_LONG).show()
                } else if (name.startsWith("__") || name.contains("/")) {
                    Toast.makeText(this, "Use a simple provider name without / or reserved __ prefix", Toast.LENGTH_LONG).show()
                } else if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://127.0.0.1") && !endpoint.startsWith("http://localhost")) {
                    Toast.makeText(this, "Provider URL must use HTTPS", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        vault.put(name, secret)
                        vault.put(providerBasePrefix + name, endpoint)
                        vault.put(providerModelPrefix + name, model)
                        showPage("Providers")
                        Toast.makeText(this, "Provider URL and credential encrypted and saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not encrypt/save credential", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun apiPage() {
        content.addView(label("API management", 23f, mainText, true))
        content.addView(label("Unified local API settings and diagnostics.", 14f, muted))
        content.addView(space(12))
        card {
            addView(label("BASE URL", 11f, muted, true))
            addView(label("http://127.0.0.1:20128/v1", 17f, mainText, true))
            addView(label("Reserved for the local OmniRoute server. Do not expose this endpoint to your LAN until authentication and access controls are implemented.", 13f, muted))
        }
        content.addView(space(12))
        card {
            addView(label("LOCAL API TOKEN", 11f, muted, true))
            val hasToken = vault.get(GATEWAY_TOKEN_KEY) != null
            addView(label(if (hasToken) "Token configured · hidden" else "No token generated", 16f, mainText, true))
            addView(label("This token is stored encrypted on-device. It is not active until the real gateway is connected.", 13f, muted))
            addView(actionButton(if (hasToken) "Regenerate local token" else "Generate local token") {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                val token = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                try {
                    vault.put(GATEWAY_TOKEN_KEY, token)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Local API token — copy it now")
                        .setMessage(token)
                        .setPositiveButton("Copy") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OmniRoute API token", token))
                            Toast.makeText(this@MainActivity, "Token copied", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Close", null).show()
                    showPage("API")
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Could not save token", Toast.LENGTH_LONG).show()
                }
            })
            addView(actionButton("Delete local token") {
                vault.remove(GATEWAY_TOKEN_KEY)
                showPage("API")
            })
        }
        content.addView(space(12))
        card {
            addView(label("DIAGNOSTICS", 11f, muted, true))
            addView(label("Gateway: not integrated", 16f, mainText, true))
            addView(label("Node.js runtime: pending Android-compatible build", 13f, muted))
            addView(label("Database: pending SQLite compatibility test", 13f, muted))
            addView(label("Streaming: not tested", 13f, muted))
        }
    }

    private fun settingsPage() {
        content.addView(label("Settings", 23f, mainText, true))
        content.addView(label("App preferences and local data controls.", 14f, muted))
        content.addView(space(12))
        card {
            addView(label("PRIVACY", 11f, muted, true))
            addView(label("Provider credentials are encrypted using an Android Keystore AES-GCM key and stored in app-private preferences.", 14f, mainText))
            addView(label("Uninstalling the app removes its private data. Clearing local data deletes saved provider credentials and the local API token.", 12f, muted))
            addView(actionButton("Delete all local credentials") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete all credentials?")
                    .setMessage("This permanently removes saved provider API keys and the local API token from this app.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete all") { _, _ ->
                        vault.clear()
                        showPage("Settings")
                        Toast.makeText(this@MainActivity, "Local credentials deleted", Toast.LENGTH_SHORT).show()
                    }.show()
            })
        }
        content.addView(space(12))
        card {
            addView(label("ABOUT THIS BUILD", 11f, muted, true))
            addView(label("OmniRoute Android · UI foundation", 17f, mainText, true))
            addView(label("This is not yet a working AI gateway. Provider routing, model discovery, chat, streaming, usage analytics, and database sync will be connected after the compatible Node.js runtime is proven.", 13f, muted))
        }
    }

    private fun card(contentBuilder: LinearLayout.() -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            contentBuilder()
        }
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    companion object {
        private const val GATEWAY_TOKEN_KEY = "__local_gateway_token__"
    }
}
