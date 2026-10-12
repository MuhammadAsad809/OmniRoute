package com.rzxi.omniroute

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "OmniRoute Android"
            textSize = 26f
            gravity = Gravity.CENTER
        }, match())
        status = TextView(this).apply {
            text = "Local service is stopped"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        layout.addView(status, match())
        layout.addView(Button(this).apply {
            text = "Start local service"
            setOnClickListener {
                val intent = Intent(this@MainActivity, OmniRouteService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
                status.text = "Starting local service…"
            }
        }, match())
        layout.addView(Button(this).apply {
            text = "Stop local service"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OmniRouteService::class.java))
                status.text = "Local service stopped"
            }
        }, match())
        layout.addView(TextView(this).apply {
            text = "Development scaffold only. The OmniRoute Node.js gateway is not integrated yet."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }, match())
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (OmniRouteService.isRunning) {
            "Local service running at 127.0.0.1:${OmniRouteService.PORT}"
        } else "Local service is stopped"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
