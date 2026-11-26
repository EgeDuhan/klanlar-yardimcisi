package com.egeduhan.klanlar

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var tvDurum: TextView
    private lateinit var btnBaslat: Button
    private val handler = Handler(Looper.getMainLooper())

    private var aktifMi = false
    private var turDevamEdiyor = false

    private var sonAskerZamani = 0L
    private var sonYagmaZamani = 0L
    private var sonBinaZamani = 0L
    private var sonTemizlikZamani = 0L
    private var sonBorsaZamani = 0L

    private val askerBirimleri = listOf(
        "spear" to "Mızrakçı",
        "sword" to "Kılıç ustası",
        "axe" to "Baltacı",
        "spy" to "Casus",
        "light" to "Hafif atlı",
        "heavy" to "Ağır atlı"
    )
    private var seciliBirimIndex = 0

    private var yagmaSablonu = "A" // "A" veya "B"
    private var borsaSatisModu = true // true = Hammadde Sat (PP Kas), false = Hammadde Al

    private val tumBinalar = listOf(
        "main" to "Ana bina",
        "barracks" to "Kışla",
        "stable" to "Ahır",
        "garage" to "Atölye",
        "watchtower" to "Gözetleme Kulesi",
        "snob" to "Akademi",
        "smith" to "Demirci",
        "place" to "İçtima Meydanı",
        "statue" to "Heykel",
        "market" to "Pazar",
        "wood" to "Oduncu",
        "stone" to "Kil ocağı",
        "iron" to "Demir madeni",
        "farm" to "Çiftlik",
        "storage" to "Depo",
        "hide" to "Gizli depo",
        "wall" to "Sur"
    )
    private var seciliBinaIndex = 10 // Oduncu

    data class BinaGorevi(val kod: String, val ad: String, val hedefSeviye: Int)
    private val binaKuyrugu = mutableListOf<BinaGorevi>()

    private val prefs by lazy { getSharedPreferences("klanlar_desktop_bot", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        tvDurum = findViewById(R.id.tvDurum)
        btnBaslat = findViewById(R.id.btnBaslatDurdur)

        tarayiciyiHazirla()
        paneliKur()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun tarayiciyiHazirla() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(BotJsBridge(), "AndroidBot")

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                captchaKontrolEt()
            }
        }

        webView.loadUrl("https://www.klanlar.org/")
    }

    private fun paneliKur() {
        val mainPanel = findViewById<LinearLayout>(R.id.mainPanel)
        val btnFloatingIcon = findViewById<Button>(R.id.btnFloatingIcon)
        val dragHeader = findViewById<LinearLayout>(R.id.dragHeader)
        val btnMinimize = findViewById<Button>(R.id.btnMinimize)

        val sekmeler = listOf(
            findViewById<Button>(R.id.tabMerkez) to findViewById<LinearLayout>(R.id.layoutMerkez),
            findViewById<Button>(R.id.tabAsker) to findViewById<LinearLayout>(R.id.layoutAsker),
            findViewById<Button>(R.id.tabYagma) to findViewById<LinearLayout>(R.id.layoutYagma),
            findViewById<Button>(R.id.tabBina) to findViewById<LinearLayout>(R.id.layoutBina),
            findViewById<Button>(R.id.tabTemiz) to findViewById<LinearLayout>(R.id.layoutTemizlik),
            findViewById<Button>(R.id.tabBorsa) to findViewById<LinearLayout>(R.id.layoutBorsa)
        )

        sekmeler.forEach { (btn, layout) ->
            btn.setOnClickListener {
                sekmeler.forEach { (b, l) ->
                    b.setBackgroundColor(Color.parseColor("#4A321B"))
                    l.visibility = View.GONE
                }
                btn.setBackgroundColor(Color.parseColor("#6E4B2A"))
                layout.visibility = View.VISIBLE
            }
        }

        // Sürüklenebilir Panel
        var dX = 0f
        var dY = 0f
        dragHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = mainPanel.x - event.rawX
                    dY = mainPanel.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    mainPanel.x = (event.rawX + dX).coerceIn(0f, (webView.width - mainPanel.width).coerceAtLeast(0).toFloat())
                    mainPanel.y = (event.rawY + dY).coerceIn(0f, (webView.height - mainPanel.height).coerceAtLeast(0).toFloat())
                    true
                }
                else -> false
            }
        }

        btnMinimize.setOnClickListener {
            mainPanel.visibility = View.GONE
            btnFloatingIcon.visibility = View.VISIBLE
        }
        btnFloatingIcon.setOnClickListener {
            btnFloatingIcon.visibility = View.GONE
            mainPanel.visibility = View.VISIBLE
        }

        // Genel Kontroller
        findViewById<Button>(R.id.btnSayfaYenile).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnKoyGenel).setOnClickListener { ekranaGit("overview") }
        findViewById<CheckBox>(R.id.cbEkranAcikTut).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Asker Kontrolleri
        val tvSeciliBirim = findViewById<TextView>(R.id.tvSeciliBirim)
        tvSeciliBirim.text = askerBirimleri[seciliBirimIndex].second
        findViewById<Button>(R.id.btnBirimOnceki).setOnClickListener {
            seciliBirimIndex = (seciliBirimIndex - 1 + askerBirimleri.size) % askerBirimleri.size
            tvSeciliBirim.text = askerBirimleri[seciliBirimIndex].second
        }
        findViewById<Button>(R.id.btnBirimSonraki).setOnClickListener {
            seciliBirimIndex = (seciliBirimIndex + 1) % askerBirimleri.size
            tvSeciliBirim.text = askerBirimleri[seciliBirimIndex].second
        }

        // Yağma Şablonu
        val btnYagmaSablon = findViewById<Button>(R.id.btnYagmaSablon)
        btnYagmaSablon.setOnClickListener {
            yagmaSablonu = if (yagmaSablonu == "A") "B" else "A"
            btnYagmaSablon.text = yagmaSablonu
        }

        // Bina Kuyruğu
        val tvSeciliBinaAdi = findViewById<TextView>(R.id.tvSeciliBinaAdi)
        val etBinaHedefSeviye = findViewById<EditText>(R.id.etBinaHedefSeviye)
        val tvBinaKuyrugu = findViewById<TextView>(R.id.tvBinaKuyrugu)
        tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second
        kuyruguYukle(tvBinaKuyrugu)

        findViewById<Button>(R.id.btnBinaOnceki).setOnClickListener {
            seciliBinaIndex = (seciliBinaIndex - 1 + tumBinalar.size) % tumBinalar.size
            tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second
        }
        findViewById<Button>(R.id.btnBinaSonraki).setOnClickListener {
            seciliBinaIndex = (seciliBinaIndex + 1) % tumBinalar.size
            tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second
        }
        findViewById<Button>(R.id.btnBinaSirayaEkle).setOnClickListener {
            val (kod, ad) = tumBinalar[seciliBinaIndex]
            val sv = (etBinaHedefSeviye.text.toString().toIntOrNull() ?: 1).coerceIn(1, 30)
            binaKuyrugu.add(BinaGorevi(kod, ad, sv))
            kuyruguKaydet(tvBinaKuyrugu)
        }
        findViewById<Button>(R.id.btnBinaSirasiTemizle).setOnClickListener {
            binaKuyrugu.clear()
            kuyruguKaydet(tvBinaKuyrugu)
        }

        // Borsa Modu
        val btnBorsaMod = findViewById<Button>(R.id.btnBorsaMod)
        btnBorsa
