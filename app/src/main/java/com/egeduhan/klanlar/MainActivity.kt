package com.egeduhan.klanlar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1E1915.toInt())
        }

        val title = TextView(this).apply {
            text = "⚔️ Klanlar Yardımcısı Kurulumu"
            textSize = 20f
            setTextColor(0xFFF4E4BC.toInt())
            setPadding(0, 0, 0, 36)
        }

        val btnOverlay = Button(this).apply {
            text = "1. Üstte Gösterme İzni Ver"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val btnAccessibility = Button(this).apply {
            text = "2. Erişilebilirlik Servisini Aç"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(btnOverlay)
        layout.addView(btnAccessibility)
        setContentView(layout)
    }
}
