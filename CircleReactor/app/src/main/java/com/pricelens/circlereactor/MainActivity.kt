package com.pricelens.circlereactor

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.pricelens.circlereactor.capture.CaptureService
import com.pricelens.circlereactor.tap.TapAccessibilityService

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotifications()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(42, 72, 42, 42)
        }

        val title = TextView(this).apply {
            text = "Circle Reactor"
            textSize = 32f
            setTextColor(0xFF13201B.toInt())
        }

        val intro = TextView(this).apply {
            text = "For your own reaction-test game: capture screen, auto-detect 9 circles, tap when one changes color."
            textSize = 17f
            setTextColor(0xFF5E6F66.toInt())
            setPadding(0, 20, 0, 30)
        }

        status = TextView(this).apply {
            text = serviceStatusText()
            textSize = 15f
            setTextColor(0xFF173F35.toInt())
            setPadding(0, 24, 0, 24)
        }

        val accessibilityButton = Button(this).apply {
            text = "1. Enable Tap Service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val startButton = Button(this).apply {
            text = "2. Start Screen Detector"
            setOnClickListener { requestCapture() }
        }

        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                CaptureService.stop(this@MainActivity)
                status.text = "Stopped."
            }
        }

        layout.addView(title)
        layout.addView(intro)
        layout.addView(accessibilityButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(status)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) status.text = serviceStatusText()
    }

    @Deprecated("Deprecated in Android framework, still fine for a minimal native Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            CaptureService.start(this, resultCode, data)
            status.text = "Running. Open your test game. Keep all 9 circles visible for 1-2 seconds."
        } else if (requestCode == REQ_CAPTURE) {
            status.text = "Screen capture permission was denied."
        }
    }

    private fun requestCapture() {
        if (TapAccessibilityService.instance == null) {
            AlertDialog.Builder(this)
                .setTitle("Tap service disabled")
                .setMessage("Enable Circle Reactor in Accessibility first, otherwise detection can see the screen but cannot tap.")
                .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Continue capture") { _, _ -> launchProjectionIntent() }
                .show()
            return
        }
        launchProjectionIntent()
    }

    private fun launchProjectionIntent() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    private fun serviceStatusText(): String {
        val tap = if (TapAccessibilityService.instance != null) "enabled" else "disabled"
        return "Accessibility tap service: $tap"
    }

    companion object {
        private const val REQ_CAPTURE = 5001
        private const val REQ_NOTIFICATIONS = 5002
    }
}
