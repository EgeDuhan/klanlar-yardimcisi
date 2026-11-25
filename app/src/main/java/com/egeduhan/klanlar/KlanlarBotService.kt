package com.egeduhan.klanlar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class KlanlarBotService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var targetPickerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var aktifMi = false

    private val prefs by lazy { getSharedPreferences("klanlar_bot_prefs", Context.MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        arayuzuKur()
    }

    private fun klavyeOdagiAc(acik: Boolean) {
        overlayParams.flags = if (acik) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        windowManager.updateViewLayout(overlayView, overlayParams)
    }

    private fun arayuzuKur() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)

        // Varsayılan olarak FLAG_NOT_FOCUSABLE aktif: arka plandaki oyuna yazı yazmayı engellemez
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 180
        }

        val mainPanel = overlayView.findViewById<LinearLayout>(R.id.mainPanel)
        val btnFloatingIcon = overlayView.findViewById<Button>(R.id.btnFloatingIcon)
        val dragHeader = overlayView.findViewById<LinearLayout>(R.id.dragHeader)
        val btnMinimize = overlayView.findViewById<Button>(R.id.btnMinimize)

        val layoutAsker = overlayView.findViewById<LinearLayout>(R.id.layoutAsker)
        val layoutYagma = overlayView.findViewById<LinearLayout>(R.id.layoutYagma)
        val layoutBina = overlayView.findViewById<LinearLayout>(R.id.layoutBina)

        val btnBaslat = overlayView.findViewById<Button>(R.id.btnBaslatDurdur)
        val tvDurum = overlayView.findViewById<TextView>(R.id.tvDurum)

        // Panel içindeki yazı kutularına dokunulduğunda klavye odağını aç
        val editIds = listOf(R.id.etAskerDakika, R.id.etYagmaSayi, R.id.etYagmaDakika, R.id.etBinaSirasi)
        editIds.forEach { id ->
            overlayView.findViewById<EditText>(id).setOnTouchListener { v, _ ->
                klavyeOdagiAc(true)
                v.requestFocus()
                false
            }
        }

        // Panel sürükleme (dokunulduğunda arka plan odağını geri verir)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    klavyeOdagiAc(false)
                    initialX = overlayParams.x
                    initialY = overlayParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    overlayParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, overlayParams)
                    true
                }
                else -> false
            }
        }

        // Küçült / Büyüt
        btnMinimize.setOnClickListener {
            klavyeOdagiAc(false)
            mainPanel.visibility = View.GONE
            btnFloatingIcon.visibility = View.VISIBLE
        }
        btnFloatingIcon.setOnClickListener {
            klavyeOdagiAc(false)
            btnFloatingIcon.visibility = View.GONE
            mainPanel.visibility = View.VISIBLE
        }

        // Sekmeler
        overlayView.findViewById<Button>(R.id.tabAsker).setOnClickListener {
            klavyeOdagiAc(false)
            layoutAsker.visibility = View.VISIBLE
            layoutYagma.visibility = View.GONE
            layoutBina.visibility = View.GONE
        }
        overlayView.findViewById<Button>(R.id.tabYagma).setOnClickListener {
            klavyeOdagiAc(false)
            layoutAsker.visibility = View.GONE
            layoutYagma.visibility = View.VISIBLE
            layoutBina.visibility = View.GONE
        }
        overlayView.findViewById<Button>(R.id.tabBina).setOnClickListener {
            klavyeOdagiAc(false)
            layoutAsker.visibility = View.GONE
            layoutYagma.visibility = View.GONE
            layoutBina.visibility = View.VISIBLE
        }

        // Koordinat Seçici Butonları
        overlayView.findViewById<Button>(R.id.btnHedefAsker).setOnClickListener {
            noktaSec("asker", "Kışla 'Eğit' butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnHedefYagma).setOnClickListener {
            noktaSec("yagma", "Yağma 'A' butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnHedefBina1).setOnClickListener {
            noktaSec("bina_1", "Oduncu yükseltme butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnHedefBina2).setOnClickListener {
            noktaSec("bina_2", "Kil Ocağı yükseltme butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnHedefBina3).setOnClickListener {
            noktaSec("bina_3", "Demir Madeni yükseltme butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnHedefBina4).setOnClickListener {
            noktaSec("bina_4", "Depo yükseltme butonuna dokun", tvDurum)
        }

        // Başlat / Durdur
        btnBaslat.setOnClickListener {
            klavyeOdagiAc(false)
            aktifMi = !aktifMi
            if (aktifMi) {
                btnBaslat.text = "SİSTEMİ DURDUR"
                btnBaslat.setBackgroundColor(Color.parseColor("#A83228"))
                tvDurum.text = "Durum: Çalışıyor..."
                donguleriBaslat(tvDurum)
            } else {
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.setBackgroundColor(Color.parseColor("#4B7A24"))
                tvDurum.text = "Durum: Durduruldu"
                handler.removeCallbacksAndMessages(null)
            }
        }

        windowManager.addView(overlayView, overlayParams)
    }

    private fun noktaSec(anahtar: String, mesaj: String, tvDurum: TextView) {
        if (targetPickerView != null) return
        klavyeOdagiAc(false)

        val picker = TextView(this).apply {
            text = "🎯 $mesaj"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(120, 180, 40, 20))
        }

        val pickerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        picker.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX
                val y = event.rawY
                prefs.edit()
                    .putFloat("${anahtar}_x", x)
                    .putFloat("${anahtar}_y", y)
                    .apply()

                tvDurum.text = "Kaydedildi ($anahtar): ${x.toInt()}, ${y.toInt()}"
                windowManager.removeView(picker)
                targetPickerView = null
            }
            true
        }

        targetPickerView = picker
        windowManager.addView(picker, pickerParams)
    }

    private fun kayitliNokta(anahtar: String): Pair<Float, Float>? {
        val x = prefs.getFloat("${anahtar}_x", -1f)
        val y = prefs.getFloat("${anahtar}_y", -1f)
        return if (x >= 0f && y >= 0f) Pair(x, y) else null
    }

    private fun donguleriBaslat(tvDurum: TextView) {
        val askerAktif = overlayView.findViewById<CheckBox>(R.id.cbAskerAktif).isChecked
        val yagmaAktif = overlayView.findViewById<CheckBox>(R.id.cbYagmaAktif).isChecked
        val binaAktif = overlayView.findViewById<CheckBox>(R.id.cbBinaAktif).isChecked

        if (askerAktif) {
            val askerDk = overlayView.findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L
            val askerPeriyot = (askerDk.coerceAtLeast(1L)) * 60_000L

            val askerGorevi = object : Runnable {
                override fun run() {
                    if (!aktifMi) return
                    val nokta = kayitliNokta("asker")
                    if (nokta != null) {
                        dokun(nokta.first, nokta.second)
                        tvDurum.text = "Son işlem: Mızrak basıldı"
                    }
                    handler.postDelayed(this, askerPeriyot + (-2500..2500).random())
                }
            }
            handler.postDelayed(askerGorevi, 1500L)
        }

        if (yagmaAktif) {
            val koySayisi = overlayView.findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 10
            val yagmaDk = overlayView.findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L
            val yagmaPeriyot = (yagmaDk.coerceAtLeast(1L)) * 60_000L

            val yagmaGorevi = object : Runnable {
                override fun run() {
                    if (!aktifMi) return
                    val nokta = kayitliNokta("yagma")
                    if (nokta != null) {
                        for (i in 0 until koySayisi) {
                            handler.postDelayed({
                                if (aktifMi) {
                                    dokun(nokta.first, nokta.second)
                                    tvDurum.text = "Yağma gönderiliyor (${i + 1}/$koySayisi)"
                                }
                            }, i * (750L + (0..350).random()))
                        }
                    }
                    handler.postDelayed(this, yagmaPeriyot + (-3000..3000).random())
                }
            }
            handler.postDelayed(yagmaGorevi, 3500L)
        }

        if (binaAktif) {
            val binaGorevi = object : Runnable {
                override fun run() {
                    if (!aktifMi) return
                    val sira = overlayView.findViewById<EditText>(R.id.etBinaSirasi).text.toString().split(",")
                    sira.forEachIndexed { idx, kod ->
                        val nokta = kayitliNokta("bina_${kod.trim()}")
                        if (nokta != null) {
                            handler.postDelayed({
                                if (aktifMi) {
                                    dokun(nokta.first, nokta.second)
                                    tvDurum.text = "Bina kontrol: Sıra ${kod.trim()}"
                                }
                            }, idx * 650L)
                        }
                    }
                    handler.postDelayed(this, 25_000L)
                }
            }
            handler.postDelayed(binaGorevi, 5000L)
        }
    }

    private fun dokun(x: Float, y: Float) {
        val sapmaX = x + (-4..4).random()
        val sapmaY = y + (-4..4).random()
        val path = Path().apply { moveTo(sapmaX, sapmaY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { aktifMi = false }
    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
