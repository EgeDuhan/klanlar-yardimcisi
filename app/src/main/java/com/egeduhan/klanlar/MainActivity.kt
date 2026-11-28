package com.egeduhan.klanlar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
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
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: BackgroundWebView
    private lateinit var tvDurum: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnBaslat: Button
    private lateinit var btnFloatingIcon: Button
    private val handler = Handler(Looper.getMainLooper())

    private var aktifMi = false
    private var turDevamEdiyor = false
    private var borsaIslemde = false
    private var borsaIslemBaslangic = 0L

    private var sonAskerZamani = 0L
    private var sonYagmaZamani = 0L
    private var sonBinaZamani = 0L
    private var sonTemizlikZamani = 0L
    private var sonBorsaZamani = 0L
    private var sonSaldiriKontrolZamani = 0L
    private var borsaHizSiniriBitisZamani = 0L
    private var bilinenGelenSaldiri = 0

    private var sonDeneneKaynak = ""
    private var basarisizKaynak = ""
    private var basarisizKaynakZamani = 0L

    private val logListesi = ArrayDeque<String>()

    private val kislaBirimleri = listOf("spear" to "Mızrakçı", "sword" to "Kılıç ustası", "axe" to "Baltacı")
    private val ahirBirimleri = listOf("spy" to "Casus", "light" to "Hafif atlı", "heavy" to "Ağır atlı")
    private val atolyeBirimleri = listOf("ram" to "Şahmerdan", "catapult" to "Mancınık")

    private var kislaIndex = 0
    private var ahirIndex = 1
    private var atolyeIndex = 0

    private val tumYagmaBirimleri = listOf(
        "spear" to "Mızrakçı", "sword" to "Kılıç ustası", "axe" to "Baltacı",
        "spy" to "Casus", "light" to "Hafif atlı", "heavy" to "Ağır atlı"
    )
    private var haritaYagmaBirimIndex = 4
    private val barbarKoordinatlari = mutableListOf<String>()
    private var barbarSiraIndex = 0

    private var yagmaSablonu = "A"
    private var borsaSatisModu = false

    private val tumBinalar = listOf(
        "main" to "Ana bina", "barracks" to "Kışla", "stable" to "Ahır", "garage" to "Atölye",
        "watchtower" to "Gözetleme Kulesi", "snob" to "Akademi", "smith" to "Demirci",
        "place" to "İçtima Meydanı", "statue" to "Heykel", "market" to "Pazar",
        "wood" to "Oduncu", "stone" to "Kil ocağı", "iron" to "Demir madeni",
        "farm" to "Çiftlik", "storage" to "Depo", "hide" to "Gizli depo", "wall" to "Sur"
    )
    private var seciliBinaIndex = 10

    data class BinaGorevi(val kod: String, val ad: String, val hedefSeviye: Int)
    private val binaKuyrugu = mutableListOf<BinaGorevi>()

    private val prefs by lazy { getSharedPreferences("klanlar_pro_bot_v2", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        tvDurum = findViewById(R.id.tvDurum)
        tvLog = findViewById(R.id.tvLog)
        btnBaslat = findViewById(R.id.btnBaslatDurdur)
        btnFloatingIcon = findViewById(R.id.btnFloatingIcon)

        bildirimIzniIste()
        modernTemaUygula()
        tarayiciyiHazirla()
        paneliKur()
        ayarlariYukle()
    }

    private fun yuvarlakKutu(renk: String, yariCap: Float, kenarRenk: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(renk))
            cornerRadius = yariCap
            if (kenarRenk != null) setStroke(2, Color.parseColor(kenarRenk))
        }
    }

    private fun modernTemaUygula() {
        findViewById<LinearLayout>(R.id.mainPanel).background = yuvarlakKutu("#EB1C1C24", 28f, "#44F5C563")
        btnFloatingIcon.background = yuvarlakKutu("#E62E7D32", 100f, "#F5C563")
        btnBaslat.background = yuvarlakKutu("#2E7D32", 18f)
        findViewById<Button>(R.id.btnMinimize).background = yuvarlakKutu("#2E2E3A", 14f)

        listOf(
            R.id.btnSayfaYenile, R.id.btnKoyGenel, R.id.btnPilIzni,
            R.id.btnKislaOnceki, R.id.btnKislaSonraki,
            R.id.btnAhirOnceki, R.id.btnAhirSonraki,
            R.id.btnAtolyeOnceki, R.id.btnAtolyeSonraki,
            R.id.btnYagmaSablon, R.id.btnHaritaTara,
            R.id.btnHaritaBirimOnceki, R.id.btnHaritaBirimSonraki,
            R.id.btnBinaOnceki, R.id.btnBinaSonraki,
            R.id.btnBinaSirayaEkle, R.id.btnBinaSirasiTemizle,
            R.id.btnBorsaMod
        ).forEach { findViewById<Button>(it).background = yuvarlakKutu("#2D2D3B", 12f, "#3E3E52") }

        listOf(
            R.id.etAskerDakika, R.id.etKislaAdet, R.id.etAhirAdet, R.id.etAtolyeAdet,
            R.id.etKorumaOdun, R.id.etKorumaKil, R.id.etKorumaDemir,
            R.id.etKorumaNufus, R.id.etKuyrukSinir,
            R.id.etYagmaSayi, R.id.etYagmaDakika, R.id.etHaritaMesafe, R.id.etHaritaAskerAdet,
            R.id.etBinaDakika, R.id.etBinaHedefSeviye, R.id.etTemizlikDakika,
            R.id.etBorsaMiktar, R.id.etBorsaMinStok, R.id.etBorsaKurLimit, R.id.etBorsaSaniye
        ).forEach { findViewById<EditText>(it).background = yuvarlakKutu("#14141A", 10f, "#3A3A4A") }
    }

    private fun bildirimIzniIste() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                if (!v.hasFocus()) v.requestFocus()
            }
            false
        }

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

        fun sekmeSec(seciliBtn: Button) {
            sekmeler.forEach { (b, l) ->
                val aktif = (b == seciliBtn)
                b.background = if (aktif) yuvarlakKutu("#C89B3C", 10f) else yuvarlakKutu("#262632", 10f)
                l.visibility = if (aktif) View.VISIBLE else View.GONE
            }
        }
        sekmeler.forEach { (btn, _) -> btn.setOnClickListener { sekmeSec(btn) } }
        sekmeSec(sekmeler[0].first)

        var dX = 0f
        var dY = 0f
        dragHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = mainPanel.x - event.rawX; dY = mainPanel.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    mainPanel.x = (event.rawX + dX).coerceIn(0f, (webView.width - mainPanel.width).coerceAtLeast(0).toFloat())
                    mainPanel.y = (event.rawY + dY).coerceIn(0f, (webView.height - mainPanel.height).coerceAtLeast(0).toFloat())
                    true
                }
                else -> false
            }
        }

        btnMinimize.setOnClickListener { mainPanel.visibility = View.GONE; btnFloatingIcon.visibility = View.VISIBLE; webView.requestFocus() }
        btnFloatingIcon.setOnClickListener { btnFloatingIcon.visibility = View.GONE; mainPanel.visibility = View.VISIBLE }

        findViewById<Button>(R.id.btnPilIzni).setOnClickListener { pilKisitlamasiniKaldir() }
        findViewById<Button>(R.id.btnSayfaYenile).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnKoyGenel).setOnClickListener { ekranaGit("overview") }
        findViewById<Switch>(R.id.cbEkranAcikTut).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val tvKisla = findViewById<TextView>(R.id.tvKislaBirim)
        val tvAhir = findViewById<TextView>(R.id.tvAhirBirim)
        val tvAtolye = findViewById<TextView>(R.id.tvAtolyeBirim)

        findViewById<Button>(R.id.btnKislaOnceki).setOnClickListener { kislaIndex = (kislaIndex - 1 + kislaBirimleri.size) % kislaBirimleri.size; tvKisla.text = kislaBirimleri[kislaIndex].second }
        findViewById<Button>(R.id.btnKislaSonraki).setOnClickListener { kislaIndex = (kislaIndex + 1) % kislaBirimleri.size; tvKisla.text = kislaBirimleri[kislaIndex].second }
        findViewById<Button>(R.id.btnAhirOnceki).setOnClickListener { ahirIndex = (ahirIndex - 1 + ahirBirimleri.size) % ahirBirimleri.size; tvAhir.text = ahirBirimleri[ahirIndex].second }
        findViewById<Button>(R.id.btnAhirSonraki).setOnClickListener { ahirIndex = (ahirIndex + 1) % ahirBirimleri.size; tvAhir.text = ahirBirimleri[ahirIndex].second }
        findViewById<Button>(R.id.btnAtolyeOnceki).setOnClickListener { atolyeIndex = (atolyeIndex - 1 + atolyeBirimleri.size) % atolyeBirimleri.size; tvAtolye.text = atolyeBirimleri[atolyeIndex].second }
        findViewById<Button>(R.id.btnAtolyeSonraki).setOnClickListener { atolyeIndex = (atolyeIndex + 1) % atolyeBirimleri.size; tvAtolye.text = atolyeBirimleri[atolyeIndex].second }

        val btnYagmaSablon = findViewById<Button>(R.id.btnYagmaSablon)
        btnYagmaSablon.setOnClickListener { yagmaSablonu = if (yagmaSablonu == "A") "B" else "A"; btnYagmaSablon.text = yagmaSablonu }
        val tvHaritaBirim = findViewById<TextView>(R.id.tvHaritaBirim)
        findViewById<Button>(R.id.btnHaritaTara).setOnClickListener { haritadanBarbarlariTara() }
        findViewById<Button>(R.id.btnHaritaBirimOnceki).setOnClickListener { haritaYagmaBirimIndex = (haritaYagmaBirimIndex - 1 + tumYagmaBirimleri.size) % tumYagmaBirimleri.size; tvHaritaBirim.text = tumYagmaBirimleri[haritaYagmaBirimIndex].second }
        findViewById<Button>(R.id.btnHaritaBirimSonraki).setOnClickListener { haritaYagmaBirimIndex = (haritaYagmaBirimIndex + 1) % tumYagmaBirimleri.size; tvHaritaBirim.text = tumYagmaBirimleri[haritaYagmaBirimIndex].second }
        barbarListesiniYukle()

        val tvSeciliBinaAdi = findViewById<TextView>(R.id.tvSeciliBinaAdi)
        val etBinaHedefSeviye = findViewById<EditText>(R.id.etBinaHedefSeviye)
        val tvBinaKuyrugu = findViewById<TextView>(R.id.tvBinaKuyrugu)
        tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second
        kuyruguYukle(tvBinaKuyrugu)

        findViewById<Button>(R.id.btnBinaOnceki).setOnClickListener { seciliBinaIndex = (seciliBinaIndex - 1 + tumBinalar.size) % tumBinalar.size; tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second }
        findViewById<Button>(R.id.btnBinaSonraki).setOnClickListener { seciliBinaIndex = (seciliBinaIndex + 1) % tumBinalar.size; tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex].second }
        findViewById<Button>(R.id.btnBinaSirayaEkle).setOnClickListener {
            val (kod, ad) = tumBinalar[seciliBinaIndex]
            val sv = (etBinaHedefSeviye.text.toString().toIntOrNull() ?: 1).coerceIn(1, 30)
            binaKuyrugu.add(BinaGorevi(kod, ad, sv))
            kuyruguKaydet(tvBinaKuyrugu)
        }
        findViewById<Button>(R.id.btnBinaSirasiTemizle).setOnClickListener { binaKuyrugu.clear(); kuyruguKaydet(tvBinaKuyrugu) }

        val btnBorsaMod = findViewById<Button>(R.id.btnBorsaMod)
        btnBorsaMod.setOnClickListener { borsaSatisModu = !borsaSatisModu; btnBorsaMod.text = if (borsaSatisModu) "MOD: SAT (PP)" else "MOD: AL (HAM)" }

        btnBaslat.setOnClickListener {
            ayarlariKaydet()
            aktifMi = !aktifMi
            if (aktifMi) {
                btnBaslat.text = "SİSTEMİ DURDUR"
                btnBaslat.background = yuvarlakKutu("#B71C1C", 18f)
                btnFloatingIcon.background = yuvarlakKutu("#E62E7D32", 100f, "#F5C563")
                durumGuncelle("Otomasyon başlatıldı", logEkle = true)
                arkaPlanServisiniBaslat()
                sonAskerZamani = 0L; sonYagmaZamani = 0L; sonBinaZamani = 0L; sonTemizlikZamani = 0L; sonBorsaZamani = 0L
                borsaHizSiniriBitisZamani = 0L; basarisizKaynak = ""
                turDevamEdiyor = false; borsaIslemde = false
                anaDonguyuBaslat(tvBinaKuyrugu)
            } else {
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.background = yuvarlakKutu("#2E7D32", 18f)
                btnFloatingIcon.background = yuvarlakKutu("#E6B71C1C", 100f, "#FFFFFF")
                durumGuncelle("Durduruldu", logEkle = true)
                arkaPlanServisiniDurdur()
                turDevamEdiyor = false; borsaIslemde = false
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    private fun ayarlariKaydet() {
        prefs.edit()
            .putBoolean("saldiri_alarm", findViewById<Switch>(R.id.cbSaldiriAlarmi).isChecked)
            .putBoolean("asker_aktif", findViewById<Switch>(R.id.cbAskerAktif).isChecked)
            .putString("asker_dk", findViewById<EditText>(R.id.etAskerDakika).text.toString())
            .putBoolean("kisla_aktif", findViewById<Switch>(R.id.cbKislaAktif).isChecked)
            .putInt("kisla_idx", kislaIndex)
            .putString("kisla_adet", findViewById<EditText>(R.id.etKislaAdet).text.toString())
            .putBoolean("ahir_aktif", findViewById<Switch>(R.id.cbAhirAktif).isChecked)
            .putInt("ahir_idx", ahirIndex)
            .putString("ahir_adet", findViewById<EditText>(R.id.etAhirAdet).text.toString())
            .putBoolean("atolye_aktif", findViewById<Switch>(R.id.cbAtolyeAktif).isChecked)
            .putInt("atolye_idx", atolyeIndex)
            .putString("atolye_adet", findViewById<EditText>(R.id.etAtolyeAdet).text.toString())
            .putString("koruma_odun", findViewById<EditText>(R.id.etKorumaOdun).text.toString())
            .putString("koruma_kil", findViewById<EditText>(R.id.etKorumaKil).text.toString())
            .putString("koruma_demir", findViewById<EditText>(R.id.etKorumaDemir).text.toString())
            .putString("koruma_nufus", findViewById<EditText>(R.id.etKorumaNufus).text.toString())
            .putString("kuyruk_sinir", findViewById<EditText>(R.id.etKuyrukSinir).text.toString())
            .putBoolean("yagma_aktif", findViewById<Switch>(R.id.cbYagmaAktif).isChecked)
            .putString("yagma_sablon", yagmaSablonu)
            .putString("yagma_sayi", findViewById<EditText>(R.id.etYagmaSayi).text.toString())
            .putString("yagma_dk", findViewById<EditText>(R.id.etYagmaDakika).text.toString())
            .putBoolean("harita_yagma", findViewById<Switch>(R.id.cbHaritaYagmaAktif).isChecked)
            .putString("harita_mesafe", findViewById<EditText>(R.id.etHaritaMesafe).text.toString())
            .putInt("harita_birim", haritaYagmaBirimIndex)
            .putString("harita_adet", findViewById<EditText>(R.id.etHaritaAskerAdet).text.toString())
            .putBoolean("bina_aktif", findViewById<Switch>(R.id.cbBinaAktif).isChecked)
            .putBoolean("bina_pp20", findViewById<Switch>(R.id.cbBinaPremium20).isChecked)
            .putString("bina_dk", findViewById<EditText>(R.id.etBinaDakika).text.toString())
            .putBoolean("temiz_aktif", findViewById<Switch>(R.id.cbTemizlikAktif).isChecked)
            .putBoolean("temiz_pp", findViewById<Switch>(R.id.cbTemizPremium).isChecked)
            .putString("temiz_dk", findViewById<EditText>(R.id.etTemizlikDakika).text.toString())
            .putBoolean("borsa_aktif", findViewById<Switch>(R.id.cbBorsaAktif).isChecked)
            .putBoolean("borsa_satis", borsaSatisModu)
            .putString("borsa_max", findViewById<EditText>(R.id.etBorsaMiktar).text.toString())
            .putString("borsa_min", findViewById<EditText>(R.id.etBorsaMinStok).text.toString())
            .putString("borsa_kur", findViewById<EditText>(R.id.etBorsaKurLimit).text.toString())
            .putString("borsa_sn", findViewById<EditText>(R.id.etBorsaSaniye).text.toString())
            .apply()
    }

    private fun ayarlariYukle() {
        findViewById<Switch>(R.id.cbSaldiriAlarmi).isChecked = prefs.getBoolean("saldiri_alarm", true)
        findViewById<Switch>(R.id.cbAskerAktif).isChecked = prefs.getBoolean("asker_aktif", false)
        findViewById<EditText>(R.id.etAskerDakika).setText(prefs.getString("asker_dk", "7"))

        findViewById<Switch>(R.id.cbKislaAktif).isChecked = prefs.getBoolean("kisla_aktif", false)
        kislaIndex = prefs.getInt("kisla_idx", 0) % kislaBirimleri.size
        findViewById<TextView>(R.id.tvKislaBirim).text = kislaBirimleri[kislaIndex].second
        findViewById<EditText>(R.id.etKislaAdet).setText(prefs.getString("kisla_adet", "5"))

        findViewById<Switch>(R.id.cbAhirAktif).isChecked = prefs.getBoolean("ahir_aktif", false)
        ahirIndex = prefs.getInt("ahir_idx", 1) % ahirBirimleri.size
        findViewById<TextView>(R.id.tvAhirBirim).text = ahirBirimleri[ahirIndex].second
        findViewById<EditText>(R.id.etAhirAdet).setText(prefs.getString("ahir_adet", "5"))

        findViewById<Switch>(R.id.cbAtolyeAktif).isChecked = prefs.getBoolean("atolye_aktif", false)
        atolyeIndex = prefs.getInt("atolye_idx", 0) % atolyeBirimleri.size
        findViewById<TextView>(R.id.tvAtolyeBirim).text = atolyeBirimleri[atolyeIndex].second
        findViewById<EditText>(R.id.etAtolyeAdet).setText(prefs.getString("atolye_adet", "2"))

        findViewById<EditText>(R.id.etKorumaOdun).setText(prefs.getString("koruma_odun", "0"))
        findViewById<EditText>(R.id.etKorumaKil).setText(prefs.getString("koruma_kil", "0"))
        findViewById<EditText>(R.id.etKorumaDemir).setText(prefs.getString("koruma_demir", "0"))
        findViewById<EditText>(R.id.etKorumaNufus).setText(prefs.getString("koruma_nufus", "100"))
        findViewById<EditText>(R.id.etKuyrukSinir).setText(prefs.getString("kuyruk_sinir", "5"))

        findViewById<Switch>(R.id.cbYagmaAktif).isChecked = prefs.getBoolean("yagma_aktif", false)
        yagmaSablonu = prefs.getString("yagma_sablon", "A") ?: "A"
        findViewById<Button>(R.id.btnYagmaSablon).text = yagmaSablonu
        findViewById<EditText>(R.id.etYagmaSayi).setText(prefs.getString("yagma_sayi", "15"))
        findViewById<EditText>(R.id.etYagmaDakika).setText(prefs.getString("yagma_dk", "5"))

        findViewById<Switch>(R.id.cbHaritaYagmaAktif).isChecked = prefs.getBoolean("harita_yagma", false)
        findViewById<EditText>(R.id.etHaritaMesafe).setText(prefs.getString("harita_mesafe", "12"))
        haritaYagmaBirimIndex = prefs.getInt("harita_birim", 4) % tumYagmaBirimleri.size
        findViewById<TextView>(R.id.tvHaritaBirim).text = tumYagmaBirimleri[haritaYagmaBirimIndex].second
        findViewById<EditText>(R.id.etHaritaAskerAdet).setText(prefs.getString("harita_adet", "5"))

        findViewById<Switch>(R.id.cbBinaAktif).isChecked = prefs.getBoolean("bina_aktif", false)
        findViewById<Switch>(R.id.cbBinaPremium20).isChecked = prefs.getBoolean("bina_pp20", false)
        findViewById<EditText>(R.id.etBinaDakika).setText(prefs.getString("bina_dk", "4"))

        findViewById<Switch>(R.id.cbTemizlikAktif).isChecked = prefs.getBoolean("temiz_aktif", false)
        findViewById<Switch>(R.id.cbTemizPremium).isChecked = prefs.getBoolean("temiz_pp", false)
        findViewById<EditText>(R.id.etTemizlikDakika).setText(prefs.getString("temiz_dk", "20"))

        findViewById<Switch>(R.id.cbBorsaAktif).isChecked = prefs.getBoolean("borsa_aktif", false)
        borsaSatisModu = prefs.getBoolean("borsa_satis", false)
        findViewById<Button>(R.id.btnBorsaMod).text = if (borsaSatisModu) "MOD: SAT (PP)" else "MOD: AL (HAM)"
        findViewById<EditText>(R.id.etBorsaMiktar).setText(prefs.getString("borsa_max", "1000"))
        findViewById<EditText>(R.id.etBorsaMinStok).setText(prefs.getString("borsa_min", "15"))
        findViewById<EditText>(R.id.etBorsaKurLimit).setText(prefs.getString("borsa_kur", "1"))
        findViewById<EditText>(R.id.etBorsaSaniye).setText(prefs.getString("borsa_sn", "1.2"))
    }

    private fun durumGuncelle(metin: String, logEkle: Boolean = false) {
        tvDurum.text = "Durum: $metin"
        if (logEkle) {
            val saat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logListesi.addFirst("[$saat] $metin")
            while (logListesi.size > 25) logListesi.removeLast()
            tvLog.text = logListesi.joinToString("\n")
        }
        if (aktifMi) {
            val intent = Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_UPDATE
                putExtra(BotForegroundService.EXTRA_STATUS, metin)
            }
            runCatching { startService(intent) }
        }
    }

    private fun anaDonguyuBaslat(tvBinaKuyrugu: TextView) {
        val dongu = object : Runnable {
            override fun run() {
                if (!aktifMi) return
                val simdi = System.currentTimeMillis()

                if (findViewById<Switch>(R.id.cbSaldiriAlarmi).isChecked && simdi - sonSaldiriKontrolZamani >= 10_000L) {
                    sonSaldiriKontrolZamani = simdi
                    gelenSaldiriKontrolEt()
                }

                val borsaAktif = findViewById<Switch>(R.id.cbBorsaAktif).isChecked
                if (borsaIslemde && (simdi - borsaIslemBaslangic > 1800L)) borsaIslemde = false

                if (borsaAktif && !turDevamEdiyor && !borsaIslemde) {
                    if (simdi < borsaHizSiniriBitisZamani) {
                        val kalanSn = ((borsaHizSiniriBitisZamani - simdi) / 1000L) + 1
                        durumGuncelle("[Borsa] ⏳ Sunucu yoğunluk molası (${kalanSn}s)...")
                    } else {
                        val borsaSnText = findViewById<EditText>(R.id.etBorsaSaniye).text.toString().replace(",", ".")
                        val borsaSureMs = ((borsaSnText.toDoubleOrNull() ?: 1.2) * 1000.0).toLong().coerceAtLeast(1000L)
                        if (simdi - sonBorsaZamani >= borsaSureMs) {
                            sonBorsaZamani = simdi
                            borsaKontrolVeOnayEt()
                        }
                    }
                }

                if (!turDevamEdiyor && !borsaIslemde) {
                    val askerAktif = findViewById<Switch>(R.id.cbAskerAktif).isChecked
                    val yagmaAktif = findViewById<Switch>(R.id.cbYagmaAktif).isChecked
                    val haritaYagmaAktif = findViewById<Switch>(R.id.cbHaritaYagmaAktif).isChecked
                    val binaAktif = findViewById<Switch>(R.id.cbBinaAktif).isChecked
                    val temizlikAktif = findViewById<Switch>(R.id.cbTemizlikAktif).isChecked

                    val askerSure = (findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L).coerceAtLeast(1L) * 60_000L
                    val yagmaSure = (findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L).coerceAtLeast(1L) * 60_000L
                    val binaSure = (findViewById<EditText>(R.id.etBinaDakika).text.toString().toLongOrNull() ?: 4L).coerceAtLeast(1L) * 60_000L
                    val temizlikSure = (findViewById<EditText>(R.id.etTemizlikDakika).text.toString().toLongOrNull() ?: 20L).coerceAtLeast(1L) * 60_000L

                    when {
                        askerAktif && (simdi - sonAskerZamani >= askerSure) -> { sonAskerZamani = simdi; cokluAskerTurunuBaslat() }
                        haritaYagmaAktif && barbarKoordinatlari.isNotEmpty() && (simdi - sonYagmaZamani >= yagmaSure) -> { sonYagmaZamani = simdi; haritaBarbarYagmaBaslat() }
                        yagmaAktif && (simdi - sonYagmaZamani >= yagmaSure) -> { sonYagmaZamani = simdi; yagmaTurunuCalistir() }
                        binaAktif && binaKuyrugu.isNotEmpty() && (simdi - sonBinaZamani >= binaSure) -> { sonBinaZamani = simdi; binaTurunuCalistir(tvBinaKuyrugu) }
                        temizlikAktif && (simdi - sonTemizlikZamani >= temizlikSure) -> { sonTemizlikZamani = simdi; temizlikTurunuCalistir() }
                    }
                }
                handler.postDelayed(this, 180L)
            }
        }
        handler.postDelayed(dongu, 200L)
    }

    private fun gelenSaldiriKontrolEt() {
        val js = "(function(){ var el = document.getElementById('incomings_amount'); return el ? (parseInt(el.innerText.trim(),10)||0) : 0; })();"
        webView.evaluateJavascript(js) { sonuc ->
            val sayi = sonuc?.trim('"')?.toIntOrNull() ?: 0
            if (sayi > bilinenGelenSaldiri) {
                durumGuncelle("🚨 GELEN SALDIRI TESPİT EDİLDİ! ($sayi saldırı)", logEkle = true)
                runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1800) }
            }
            bilinenGelenSaldiri = sayi
        }
    }

    // --- 1. ÇOKLU ASKER ÜRETİMİ (KIŞLA + AHIR + ATÖLYE + DEPO/NÜFUS/KUYRUK KORUMA) ---
    data class AskerGorevi(val ekran: String, val binaAd: String, val birimKod: String, val birimAd: String, val adet: Int)

    private fun cokluAskerTurunuBaslat() {
        val gorevler = mutableListOf<AskerGorevi>()
        if (findViewById<Switch>(R.id.cbKislaAktif).isChecked) {
            val (kod, ad) = kislaBirimleri[kislaIndex]
            val adet = (findViewById<EditText>(R.id.etKislaAdet).text.toString().toIntOrNull() ?: 5).coerceIn(1, 500)
            gorevler.add(AskerGorevi("barracks", "Kışla", kod, ad, adet))
        }
        if (findViewById<Switch>(R.id.cbAhirAktif).isChecked) {
            val (kod, ad) = ahirBirimleri[ahirIndex]
            val adet = (findViewById<EditText>(R.id.etAhirAdet).text.toString().toIntOrNull() ?: 5).coerceIn(1, 500)
            gorevler.add(AskerGorevi("stable", "Ahır", kod, ad, adet))
        }
        if (findViewById<Switch>(R.id.cbAtolyeAktif).isChecked) {
            val (kod, ad) = atolyeBirimleri[atolyeIndex]
            val adet = (findViewById<EditText>(R.id.etAtolyeAdet).text.toString().toIntOrNull() ?: 2).coerceIn(1, 500)
            gorevler.add(AskerGorevi("garage", "Atölye", kod, ad, adet))
        }
        if (gorevler.isEmpty()) return
        turDevamEdiyor = true
        siradakiAskerGoreviniYap(gorevler, 0)
    }

    private fun siradakiAskerGoreviniYap(gorevler: List<AskerGorevi>, index: Int) {
        if (!aktifMi || index >= gorevler.size) { turDevamEdiyor = false; return }
        val g = gorevler[index]
        val korumaOdun = findViewById<EditText>(R.id.etKorumaOdun).text.toString().toIntOrNull() ?: 0
        val korumaKil = findViewById<EditText>(R.id.etKorumaKil).text.toString().toIntOrNull() ?: 0
        val korumaDemir = findViewById<EditText>(R.id.etKorumaDemir).text.toString().toIntOrNull() ?: 0
        val korumaNufus = findViewById<EditText>(R.id.etKorumaNufus).text.toString().toIntOrNull() ?: 0
        val maxKuyruk = (findViewById<EditText>(R.id.etKuyrukSinir).text.toString().toIntOrNull() ?: 5).coerceAtLeast(1)

        durumGuncelle("[Asker] ${g.binaAd} açılıyor (${g.birimAd})...")
        if (!ekranaGit(g.ekran)) { turDevamEdiyor = false; return }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val js = """
                (function() {
                    var maliyetler = {
                        spear:{w:50,s:30,i:10,p:1}, sword:{w:30,s:30,i:70,p:1}, axe:{w:60,s:30,i:40,p:1},
                        spy:{w:50,s:50,i:20,p:2}, light:{w:125,s:100,i:250,p:4}, heavy:{w:200,s:150,i:600,p:6},
                        ram:{w:300,s:200,i:200,p:5}, catapult:{w:320,s:400,i:100,p:8}
                    };
                    var m = maliyetler['${g.birimKod}'];
                    var inp = document.querySelector('input[name="${g.birimKod}"]');
                    var btn = document.querySelector('input.btn-recruit');
                    if (!m || !inp || !btn) return JSON.stringify({ok:false, neden:'bina_yok'});

                    var kuyruk = document.querySelectorAll('.trainqueue_wrap a.btn-cancel, #trainqueue_wrap_${g.ekran} a.btn-cancel').length;
                    if (kuyruk >= $maxKuyruk) return JSON.stringify({ok:false, neden:'kuyruk_dolu', kuyruk:kuyruk});

                    var wood = parseInt((document.getElementById('wood') || {}).innerText || '0', 10) || 0;
                    var stone = parseInt((document.getElementById('stone') || {}).innerText || '0', 10) || 0;
                    var iron = parseInt((document.getElementById('iron') || {}).innerText || '0', 10) || 0;
                    var popCur = parseInt((document.getElementById('pop_current_label') || {}).innerText || '0', 10) || 0;
                    var popMax = parseInt((document.getElementById('pop_max_label') || {}).innerText || '0', 10) || 0;

                    var maxW = Math.floor(Math.max(0, wood - $korumaOdun) / m.w);
                    var maxS = Math.floor(Math.max(0, stone - $korumaKil) / m.s);
                    var maxI = Math.floor(Math.max(0, iron - $korumaDemir) / m.i);
                    var maxP = Math.floor(Math.max(0, (popMax - popCur) - $korumaNufus) / m.p);

                    var basilacak = Math.min(${g.adet}, maxW, maxS, maxI, maxP);
                    if (basilacak <= 0) return JSON.stringify({ok:false, neden:'koruma_limit'});

                    inp.focus(); inp.value = String(basilacak);
                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                    inp.dispatchEvent(new Event('change', {bubbles:true}));
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({ok:true, adet:basilacak, x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()
            webView.evaluateJavascript(js) { sonuc ->
                val obj = parseJsJson(sonuc)
                if (obj != null && obj.optBoolean("ok")) {
                    durumGuncelle("[Asker] ⚔️ ${g.binaAd}: ${obj.optInt("adet")} ${g.birimAd} basılıyor", logEkle = true)
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({ siradakiAskerGoreviniYap(gorevler, index + 1) }, 2000L)
                    }, 700L)
                } else {
                    val neden = when (obj?.optString("neden")) {
                        "kuyruk_dolu" -> "${g.binaAd} kuyruğu dolu (${obj.optInt("kuyruk")})"
                        "koruma_limit" -> "${g.binaAd}: Depo/Nüfus koruma sınırında"
                        else -> "${g.binaAd}: Bina/birim uygun değil"
                    }
                    durumGuncelle("[Asker] ⏭️ $neden")
                    handler.postDelayed({ siradakiAskerGoreviniYap(gorevler, index + 1) }, 1000L)
                }
            }
        }, 2800L)
    }

    // --- 2. HARİTA BARBAR TARAMA & YAĞMA ---
    private fun haritadanBarbarlariTara() {
        val mevcutUrl = webView.url ?: ""
        if (!mevcutUrl.contains("screen=map")) { durumGuncelle("[Harita] Harita açılıyor, tekrar bas!"); ekranaGit("map"); return }
        val maxMesafe = (findViewById<EditText>(R.id.etHaritaMesafe).text.toString().toDoubleOrNull() ?: 12.0).coerceIn(1.0, 100.0)
        val js = """
            (function() {
                if (typeof TWMap === 'undefined' || !TWMap.villages || typeof game_data === 'undefined') return JSON.stringify({ok:false});
                var myX = parseInt(game_data.village.x, 10), myY = parseInt(game_data.village.y, 10), bulunanlar = [];
                Object.keys(TWMap.villages).forEach(function(key) {
                    var v = TWMap.villages[key];
                    if (!v || !(v.owner === 0 || v.owner === '0' || v.owner === false || v.owner === null)) return;
                    var xy = parseInt(v.xy || key, 10); if (isNaN(xy)) return;
                    var vx = Math.floor(xy / 1000), vy = xy % 1000;
                    if (vx === myX && vy === myY) return;
                    var dist = Math.hypot(vx - myX, vy - myY);
                    if (dist <= $maxMesafe) bulunanlar.push({coord: vx + '|' + vy, dist: dist});
                });
                bulunanlar.sort(function(a, b) { return a.dist - b.dist; });
                var tekil = [], gorulen = {};
                bulunanlar.forEach(function(item) { if (!gorulen[item.coord]) { gorulen[item.coord] = true; tekil.push(item.coord); } });
                return JSON.stringify({ok:true, liste: tekil});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                val arr: JSONArray = obj.optJSONArray("liste") ?: JSONArray()
                barbarKoordinatlari.clear()
                for (i in 0 until arr.length()) barbarKoordinatlari.add(arr.getString(i))
                barbarSiraIndex = 0; barbarListesiniKaydet()
                durumGuncelle("[Harita] ✅ ${barbarKoordinatlari.size} barbar kaydedildi!", logEkle = true)
            } else durumGuncelle("[Harita] ⚠️ Harita verisi okunamadı")
        }
    }

    private fun haritaBarbarYagmaBaslat() {
        if (barbarKoordinatlari.isEmpty()) return
        turDevamEdiyor = true; haritaSiradakiBarbaraSaldir()
    }

    private fun haritaSiradakiBarbaraSaldir() {
        if (!aktifMi || !findViewById<Switch>(R.id.cbHaritaYagmaAktif).isChecked || barbarKoordinatlari.isEmpty()) { turDevamEdiyor = false; return }
        val koordinat = barbarKoordinatlari[barbarSiraIndex % barbarKoordinatlari.size]
        val parcalar = koordinat.split("|")
        if (parcalar.size != 2) { turDevamEdiyor = false; return }
        val (birimKod, birimAd) = tumYagmaBirimleri[haritaYagmaBirimIndex]
        val adet = (findViewById<EditText>(R.id.etHaritaAskerAdet).text.toString().toIntOrNull() ?: 5).coerceIn(1, 5000)

        durumGuncelle("[Harita Yağma] $koordinat hazırlanıyor...")
        if (!ekranaGit("place")) { turDevamEdiyor = false; return }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val jsForm = """
                (function() {
                    var unitInp = document.querySelector('input[name="$birimKod"]');
                    var xInp = document.querySelector('input[name="x"]');
                    var yInp = document.querySelector('input[name="y"]');
                    var btn = document.getElementById('target_attack');
                    if (!unitInp || !xInp || !yInp || !btn) return JSON.stringify({ok:false});
                    var link = document.querySelector('a.units-entry-all[data-unit="$birimKod"]');
                    var mevcut = link ? (parseInt((link.innerText.match(/(\d+)/)||[0,0])[1],10)||0) : 0;
                    if (mevcut < $adet) return JSON.stringify({ok:false});
                    unitInp.value = '$adet'; xInp.value = '${parcalar[0]}'; yInp.value = '${parcalar[1]}';
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()
            webView.evaluateJavascript(jsForm) { sonuc ->
                val obj = parseJsJson(sonuc)
                if (obj != null && obj.optBoolean("ok")) {
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({ haritaSaldiriOnayla(koordinat, "$adet $birimAd") }, 2200L)
                    }, 500L)
                } else { durumGuncelle("[Harita Yağma] Yeterli $birimAd kalmadı"); turDevamEdiyor = false }
            }
        }, 2600L)
    }

    private fun haritaSaldiriOnayla(koordinat: String, bilgi: String) {
        if (!aktifMi) { turDevamEdiyor = false; return }
        val jsOnay = """
            (function() {
                var btn = document.getElementById('troop_confirm_go');
                if (!btn || btn.offsetParent === null) return JSON.stringify({ok:false});
                btn.scrollIntoView({block:'center'});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsOnay) { sonuc ->
            val obj = parseJsJson(sonuc)
            barbarSiraIndex = (barbarSiraIndex + 1) % barbarKoordinatlari.size
            barbarListesiniKaydet()
            if (obj != null && obj.optBoolean("ok")) {
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                durumGuncelle("[Harita] ✅ $koordinat ($bilgi) çıkıldı", logEkle = true)
            }
            handler.postDelayed({ haritaSiradakiBarbaraSaldir() }, (1600L..2300L).random())
        }
    }

    // --- 3. YAĞMA ASİSTANI ---
    private fun yagmaTurunuCalistir() {
        val koySayisi = (findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 15).coerceIn(1, 100)
        val sinif = if (yagmaSablonu == "A") "farm_icon_a" else "farm_icon_b"
        turDevamEdiyor = true
        if (!ekranaGit("am_farm")) { turDevamEdiyor = false; return }
        handler.postDelayed({ yagmaSiradakiKoyeBas(sinif, 1, koySayisi) }, 3000L)
    }

    private fun yagmaSiradakiKoyeBas(sinif: String, mevcut: Int, toplam: Int) {
        if (!aktifMi || mevcut > toplam) { turDevamEdiyor = false; return }
        val js = """
            (function() {
                var btns = Array.from(document.querySelectorAll('a.$sinif:not(.farm_icon_disabled)'));
                if (btns.length === 0) return JSON.stringify({ok:false});
                var b = btns[0]; b.scrollIntoView({block:'center'});
                var r = b.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                durumGuncelle("[Yağma] 🐎 $yagmaSablonu ($mevcut/$toplam)")
                handler.postDelayed({
                    webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                    handler.postDelayed({ yagmaSiradakiKoyeBas(sinif, mevcut + 1, toplam) }, (1300L..1950L).random())
                }, 400L)
            } else turDevamEdiyor = false
        }
    }

    // --- 4. BİNA ---
    private fun binaTurunuCalistir(tvBinaKuyrugu: TextView) {
        val gorev = binaKuyrugu.firstOrNull() ?: return
        val premium20Kullan = findViewById<Switch>(R.id.cbBinaPremium20).isChecked
        turDevamEdiyor = true
        if (!ekranaGit("main")) { turDevamEdiyor = false; return }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val js = """
                (function() {
                    var row = document.getElementById('main_buildrow_${gorev.kod}');
                    if (!row) return JSON.stringify({durum:'yok'});
                    var m = (row.innerText || '').match(/Seviye\s*(\d+)/i);
                    var sv = m ? parseInt(m[1]) : 0;
                    if (sv >= ${gorev.hedefSeviye}) return JSON.stringify({durum:'tamam'});
                    var btn = $premium20Kullan
                        ? Array.from(row.querySelectorAll('a,button,input')).find(function(el){ return (el.innerText||el.value||'').indexOf('-20%')!==-1 && el.offsetParent!==null && !el.classList.contains('btn-disabled'); })
                        : row.querySelector('a.btn-build');
                    if (!btn || btn.offsetParent === null) return JSON.stringify({durum:'bekle'});
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({durum:'bas', x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()
            webView.evaluateJavascript(js) { sonuc ->
                val obj = parseJsJson(sonuc)
                when (obj?.optString("durum")) {
                    "tamam" -> {
                        durumGuncelle("[Bina] ✅ ${gorev.ad} Sv.${gorev.hedefSeviye} tamam!", logEkle = true)
                        binaKuyrugu.remove(gorev); kuyruguKaydet(tvBinaKuyrugu); turDevamEdiyor = false
                    }
                    "bas" -> {
                        durumGuncelle("[Bina] 🔨 ${gorev.ad} yükseltiliyor", logEkle = true)
                        handler.postDelayed({
                            webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                            if (premium20Kullan) handler.postDelayed({ onayPenceresiniTamamla(1, "[Bina]") }, 1200L)
                            else handler.postDelayed({ turDevamEdiyor = false }, 2000L)
                        }, 700L)
                    }
                    else -> turDevamEdiyor = false
                }
            }
        }, 3000L)
    }

    // --- 5. TEMİZLİK (e79d7aa STABİL MOTOR + %20 PP DESTEKLİ) ---
    private fun temizlikTurunuCalistir() {
        val seciliSeviyeler = mutableListOf<Int>()
        if (findViewById<CheckBox>(R.id.cbTemizLv1).isChecked) seciliSeviyeler.add(1)
        if (findViewById<CheckBox>(R.id.cbTemizLv2).isChecked) seciliSeviyeler.add(2)
        if (findViewById<CheckBox>(R.id.cbTemizLv3).isChecked) seciliSeviyeler.add(3)
        if (findViewById<CheckBox>(R.id.cbTemizLv4).isChecked) seciliSeviyeler.add(4)

        if (seciliSeviyeler.isEmpty()) {
            durumGuncelle("[Temizlik] En az 1 temizlik seviyesi tikle!")
            return
        }

        turDevamEdiyor = true
        durumGuncelle("[Temizlik] İçtima Meydanı açılıyor...")
        if (!ekranaGit("place", "&mode=scavenge")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            temizlikSiradakiKoluGonder(seciliSeviyeler, 0)
        }, 3000L)
    }

    private fun temizlikSiradakiKoluGonder(seciliSeviyeler: List<Int>, index: Int) {
        if (!aktifMi || index >= seciliSeviyeler.size) {
            turDevamEdiyor = false
            return
        }

        val hedefLv = seciliSeviyeler[index]
        val kalanSeviyelerJson = seciliSeviyeler.subList(index, seciliSeviyeler.size).joinToString(",")
        val premiumKullan = findViewById<Switch>(R.id.cbTemizPremium).isChecked
        val btnSecici = if (premiumKullan) "a.premium_send_button:not(.btn-disabled), a.btn-pp:not(.btn-disabled)" else "a.free_send_button:not(.btn-disabled)"

        val js = """
            (function() {
                var agirliklar = {1: 15, 2: 6, 3: 3, 4: 2};
                var kalanlar = [$kalanSeviyelerJson];
                var hedef = $hedefLv;

                var kutular = Array.from(document.querySelectorAll('.scavenge-option'));
                var hedefKutu = kutular[hedef - 1];
                if (!hedefKutu) return JSON.stringify({ok:false, neden:'kutu_yok'});

                var btn = hedefKutu.querySelector('$btnSecici');
                if (!btn) return JSON.stringify({ok:false, neden:'mesgul'});

                var toplamAgirlik = 0;
                kalanlar.forEach(function(lv) {
                    var k = kutular[lv - 1];
                    if (k && k.querySelector('$btnSecici')) {
                        toplamAgirlik += (agirliklar[lv] || 1);
                    }
                });
                if (toplamAgirlik <= 0) toplamAgirlik = agirliklar[hedef] || 1;
                var payOrani = (agirliklar[hedef] || 1) / toplamAgirlik;

                var birimler = ['spear', 'sword', 'axe', 'light', 'heavy'];
                var toplamGonderilen = 0;

                birimler.forEach(function(u) {
                    var inp = document.querySelector('input.unitsInput[name="' + u + '"]');
                    if (!inp) return;
                    var link = document.querySelector('a.units-entry-all[data-unit="' + u + '"]');
                    var mevcut = 0;
                    if (link) {
                        var m = (link.innerText || '').match(/(\d+)/);
                        if (m) mevcut = parseInt(m[1], 10);
                    }
                    if (mevcut > 0) {
                        var adet = Math.floor(mevcut * payOrani);
                        if (adet < 1 && mevcut >= 1 && kalanlar.length === 1) adet = mevcut;
                        if (adet > 0) {
                            inp.focus();
                            inp.value = String(adet);
                            if (window.jQuery) {
                                window.jQuery(inp).val(String(adet)).trigger('input').trigger('keyup').trigger('change');
                            }
                            inp.dispatchEvent(new Event('input', {bubbles:true}));
                            inp.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));
                            inp.dispatchEvent(new Event('change', {bubbles:true}));
                            toplamGonderilen += adet;
                        }
                    }
                });

                if (toplamGonderilen < 10) return JSON.stringify({ok:false, neden:'asker_az'});

                btn.scrollIntoView({block:'center'});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, adet:toplamGonderilen, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                val adet = obj.optInt("adet")
                val etiket = if (premiumKullan) "(+%20 PP)" else ""
                durumGuncelle("[Temizlik] 🧹 $hedefLv. Seviye $etiket ($adet asker) gönderiliyor...", logEkle = true)
                handler.postDelayed({
                    webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                    if (premiumKullan) {
                        handler.postDelayed({ temizlikOnaylaVeDevamEt(seciliSeviyeler, index, 1) }, 900L)
                    } else {
                        handler.postDelayed({ temizlikSiradakiKoluGonder(seciliSeviyeler, index + 1) }, 1800L)
                    }
                }, 600L)
            } else {
                val neden = obj?.optString("neden") ?: ""
                if (neden == "mesgul") {
                    durumGuncelle("[Temizlik] $hedefLv. Seviye zaten yolda, sıradakine geçiliyor...")
                    temizlikSiradakiKoluGonder(seciliSeviyeler, index + 1)
                } else {
                    durumGuncelle("[Temizlik] Uygun/yeterli boşta asker yok")
                    turDevamEdiyor = false
                }
            }
        }
    }

    private fun temizlikOnaylaVeDevamEt(seciliSeviyeler: List<Int>, index: Int, deneme: Int) {
        if (!aktifMi) {
            turDevamEdiyor = false
            return
        }
        val js = """
            (function() {
                var btn = document.querySelector('.confirmation-box .evt-confirm-btn, .popup_box_content .evt-confirm-btn, .btn-confirm-yes');
                if (!btn || btn.offsetParent === null) return JSON.stringify({ok:false});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                handler.postDelayed({ temizlikSiradakiKoluGonder(seciliSeviyeler, index + 1) }, 1800L)
            } else if (deneme < 3) {
                handler.postDelayed({ temizlikOnaylaVeDevamEt(seciliSeviyeler, index, deneme + 1) }, 600L)
            } else {
                temizlikSiradakiKoluGonder(seciliSeviyeler, index + 1)
            }
        }
    }

    // --- 6. BORSA (e79d7aa HATASIZ SÜRÜMÜ - DOKUNULMADI) ---
    private fun borsaKontrolVeOnayEt() {
        val mevcutUrl = webView.url ?: return
        if (!mevcutUrl.contains("game.php")) {
            durumGuncelle("⚠️ Önce dünyaya giriş yap!")
            return
        }

        if (!mevcutUrl.contains("screen=market") || !mevcutUrl.contains("mode=exchange")) {
            borsaIslemde = true
            borsaIslemBaslangic = System.currentTimeMillis() + 1200L
            durumGuncelle("[Borsa] Premium Takası ekranına geçiliyor...")
            if (!ekranaGit("market", "&mode=exchange")) {
                borsaIslemde = false
            }
            return
        }

        val odunAktif = findViewById<CheckBox>(R.id.cbBorsaOdun).isChecked
        val kilAktif = findViewById<CheckBox>(R.id.cbBorsaKil).isChecked
        val demirAktif = findViewById<CheckBox>(R.id.cbBorsaDemir).isChecked

        val kaynaklar = mutableListOf<String>()
        if (odunAktif) kaynaklar.add("wood")
        if (kilAktif) kaynaklar.add("stone")
        if (demirAktif) kaynaklar.add("iron")
        if (kaynaklar.isEmpty()) return

        val maxMiktar = (findViewById<EditText>(R.id.etBorsaMiktar).text.toString().toIntOrNull() ?: 1000).coerceAtLeast(1)
        val minStok = (findViewById<EditText>(R.id.etBorsaMinStok).text.toString().toIntOrNull() ?: 15).coerceAtLeast(1)
        val kurLimit = (findViewById<EditText>(R.id.etBorsaKurLimit).text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
        val mod = if (borsaSatisModu) "sell" else "buy"

        val atlanacakKaynak = if (System.currentTimeMillis() - basarisizKaynakZamani < 2200L) basarisizKaynak else ""

        borsaIslemde = true
        borsaIslemBaslangic = System.currentTimeMillis()
        val kaynakJson = kaynaklar.joinToString(",") { "'$it'" }

        val js = """
            (function() {
                function gorunurMu(el) {
                    if (!el) return false;
                    var st = window.getComputedStyle(el);
                    if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 5 && r.height > 5;
                }

                function acikPencereyiIsle() {
                    var onayBtn = Array.from(document.querySelectorAll('.confirmation-box .evt-confirm-btn, .confirmation-box .btn-confirm-yes, #confirmation-box .evt-confirm-btn, .popup_box_content .evt-confirm-btn, button.evt-confirm-btn, button.btn-confirm-yes')).find(gorunurMu);
                    if (onayBtn) {
                        if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                        var r = onayBtn.getBoundingClientRect();
                        if (window.jQuery) window.jQuery(onayBtn).trigger('click');
                        onayBtn.click();
                        AndroidBot.onBorsaOnayDokun(true, r.left + r.width/2, r.top + r.height/2);
                        return true;
                    }
                    var iptalBtn = Array.from(document.querySelectorAll('.confirmation-box .evt-cancel-btn, .confirmation-box .btn-confirm-no, #confirmation-box .evt-cancel-btn, button.evt-cancel-btn, button.btn-confirm-no')).find(gorunurMu);
                    if (iptalBtn) {
                        if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                        var r2 = iptalBtn.getBoundingClientRect();
                        if (window.jQuery) window.jQuery(iptalBtn).trigger('click');
                        iptalBtn.click();
                        AndroidBot.onBorsaOnayDokun(false, r2.left + r2.width/2, r2.top + r2.height/2);
                        return true;
                    }
                    return false;
                }

                if (acikPencereyiIsle()) return;

                var mevcutHata = document.querySelector('.error_box, #error');
                if (gorunurMu(mevcutHata)) {
                    var hTxt = (mevcutHata.innerText || '').toLowerCase();
                    mevcutHata.remove();
                    if (hTxt.indexOf('yoğun') !== -1 || hTxt.indexOf('tekrar deneyin') !== -1 || hTxt.indexOf('many') !== -1) {
                        AndroidBot.onBorsaHizSiniri();
                    } else {
                        AndroidBot.onBorsaSonuc(false);
                    }
                    return;
                }

                function ilkSayi(el) {
                    if (!el) return 0;
                    var m = (el.textContent || el.innerText || '').trim().match(/(\d[\d.]*)/);
                    return m ? (parseInt(m[1].replace(/\./g, ''), 10) || 0) : 0;
                }

                function tablodanOku() {
                    var res = {
                        stock: {wood: 0, stone: 0, iron: 0},
                        capacity: {wood: 0, stone: 0, iron: 0},
                        rates: {wood: 0, stone: 0, iron: 0}
                    };
                    var sirasi = ['wood', 'stone', 'iron'];
                    sirasi.forEach(function(r) {
                        res.stock[r] = ilkSayi(document.getElementById('premium_exchange_stock_' + r));
                        res.capacity[r] = ilkSayi(document.getElementById('premium_exchange_capacity_' + r));
                        res.rates[r] = ilkSayi(document.getElementById('premium_exchange_rate_' + r));
                    });

                    Array.from(document.querySelectorAll('#premium_exchange_form tr, table.vis tr')).forEach(function(tr) {
                        var hucreler = tr.querySelectorAll('td, th');
                        if (hucreler.length < 4) return;
                        var baslik = (hucreler[0].innerText || '').trim().toLowerCase();
                        for (var i = 0; i < 3; i++) {
                            var val = ilkSayi(hucreler[i + 1]);
                            if (val > 0) {
                                if (baslik.indexOf('stok') !== -1 || baslik.indexOf('stock') !== -1) res.stock[sirasi[i]] = val;
                                else if (baslik.indexOf('kapasite') !== -1 || baslik.indexOf('capacity') !== -1) res.capacity[sirasi[i]] = val;
                                else if (baslik.indexOf('oran') !== -1 || baslik.indexOf('kur') !== -1 || baslik.indexOf('rate') !== -1) res.rates[sirasi[i]] = val;
                            }
                        }
                    });
                    return res;
                }

                function anindaGozlemciBaslat() {
                    if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                    function kontrol() {
                        var hata = document.querySelector('.error_box, #error');
                        if (gorunurMu(hata)) {
                            if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                            var msg = (hata.innerText || '').toLowerCase();
                            if (msg.indexOf('yoğun') !== -1 || msg.indexOf('tekrar deneyin') !== -1) {
                                AndroidBot.onBorsaHizSiniri();
                            } else {
                                AndroidBot.onBorsaSonuc(false);
                            }
                            return true;
                        }
                        return acikPencereyiIsle();
                    }
                    window.__klanlarOnayObserver = new MutationObserver(function() { kontrol(); });
                    window.__klanlarOnayObserver.observe(document.body, {childList: true, subtree: true, attributes: true});
                    setTimeout(kontrol, 150);
                    setTimeout(kontrol, 350);
                    setTimeout(kontrol, 700);
                }

                function islemiCalistir(veri) {
                    var hedefler = [$kaynakJson];
                    var atla = '$atlanacakKaynak';
                    var mod = '$mod';
                    var maxMiktar = $maxMiktar;
                    var minStok = $minStok;
                    var limit = $kurLimit;

                    var secilen = null;
                    for (var i = 0; i < hedefler.length; i++) {
                        var res = hedefler[i];
                        if (res === atla) continue;

                        var stok = veri.stock[res] || 0;
                        var kap = veri.capacity[res] || 0;
                        var kur = veri.rates[res] || 0;

                        if (mod === 'sell') {
                            var bosYer = kap - stok;
                            if (bosYer >= minStok && (kur <= limit || kur === 0)) {
                                secilen = {res: res, adet: Math.min(maxMiktar, bosYer), kur: kur};
                                break;
                            }
                        } else {
                            if (stok >= minStok && (kur >= limit || limit <= 1)) {
                                secilen = {res: res, adet: Math.min(maxMiktar, stok), kur: kur};
                                break;
                            }
                        }
                    }

                    if (!secilen) {
                        var ozet = 'Stok: ' + veri.stock.wood + '/' + veri.stock.stone + '/' + veri.stock.iron + ' | Oran: ' + veri.rates.wood + '/' + veri.rates.stone + '/' + veri.rates.iron;
                        AndroidBot.onBorsaBos(ozet);
                        return;
                    }

                    var calcBtn = document.querySelector('input.btn-premium-exchange-buy, #premium_exchange_form input[type="submit"]');
                    if (!calcBtn) {
                        AndroidBot.onBorsaBos('Form bulunamadı');
                        return;
                    }

                    var btnText = (calcBtn.value || '').toLowerCase();
                    if (calcBtn.disabled || calcBtn.classList.contains('btn-disabled') || btnText.indexOf('bekleyin') !== -1) {
                        AndroidBot.onBorsaBos('Oyun bekleme sayacı: ' + (calcBtn.value || 'bekleniyor'));
                        return;
                    }

                    ['buy_wood','buy_stone','buy_iron','sell_wood','sell_stone','sell_iron'].forEach(function(n) {
                        var el = document.querySelector('input[name="' + n + '"]');
                        if (el) {
                            el.value = '';
                            if (window.jQuery) window.jQuery(el).val('').trigger('input').trigger('change');
                        }
                    });

                    var inpName = (mod === 'sell' ? 'sell_' : 'buy_') + secilen.res;
                    var inp = document.querySelector('input[name="' + inpName + '"]');
                    if (!inp) {
                        AndroidBot.onBorsaBos('Kutu bulunamadı');
                        return;
                    }

                    inp.focus();
                    inp.value = String(secilen.adet);
                    if (window.jQuery) {
                        window.jQuery(inp).val(String(secilen.adet)).trigger('input').trigger('keyup').trigger('change');
                    }
                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                    inp.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));
                    inp.dispatchEvent(new Event('change', {bubbles:true}));

                    anindaGozlemciBaslat();
                    AndroidBot.onBorsaTetiklendi(secilen.res, secilen.adet, secilen.kur);

                    setTimeout(function() {
                        if (window.jQuery) window.jQuery(calcBtn).trigger('click');
                        calcBtn.click();
                    }, 60);
                }

                if (typeof TribalWars !== 'undefined' && typeof TribalWars.get === 'function') {
                    TribalWars.get('market', { ajax: 'exchange_data' }, function(data) {
                        var tablo = tablodanOku();
                        var birlesik = {
                            stock: {
                                wood: (data && data.stock && data.stock.wood !== undefined) ? parseInt(data.stock.wood, 10) : tablo.stock.wood,
                                stone: (data && data.stock && data.stock.stone !== undefined) ? parseInt(data.stock.stone, 10) : tablo.stock.stone,
                                iron: (data && data.stock && data.stock.iron !== undefined) ? parseInt(data.stock.iron, 10) : tablo.stock.iron
                            },
                            capacity: {
                                wood: (data && data.capacity && data.capacity.wood !== undefined) ? parseInt(data.capacity.wood, 10) : tablo.capacity.wood,
                                stone: (data && data.capacity && data.capacity.stone !== undefined) ? parseInt(data.capacity.stone, 10) : tablo.capacity.stone,
                                iron: (data && data.capacity && data.capacity.iron !== undefined) ? parseInt(data.capacity.iron, 10) : tablo.capacity.iron
                            },
                            rates: {
                                wood: (data && data.rates && data.rates.wood !== undefined) ? Math.floor(parseFloat(data.rates.wood)) : tablo.rates.wood,
                                stone: (data && data.rates && data.rates.stone !== undefined) ? Math.floor(parseFloat(data.rates.stone)) : tablo.rates.stone,
                                iron: (data && data.rates && data.rates.iron !== undefined) ? Math.floor(parseFloat(data.rates.iron)) : tablo.rates.iron
                            }
                        };
                        ['wood', 'stone', 'iron'].forEach(function(r) {
                            var sEl = document.getElementById('premium_exchange_stock_' + r);
                            if (sEl) sEl.innerText = birlesik.stock[r];
                        });
                        islemiCalistir(birlesik);
                    }, function() {
                        islemiCalistir(tablodanOku());
                    });
                } else {
                    islemiCalistir(tablodanOku());
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun borsaKutulariniTemizle() {
        val js = """
            (function() {
                if (window.__klanlarOnayObserver) {
                    window.__klanlarOnayObserver.disconnect();
                    window.__klanlarOnayObserver = null;
                }
                ['buy_wood','buy_stone','buy_iron','sell_wood','sell_stone','sell_iron'].forEach(function(n) {
                    var el = document.querySelector('input[name="' + n + '"]');
                    if (el) {
                        el.value = '';
                        if (window.jQuery) window.jQuery(el).val('').trigger('change');
                    }
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun ekranaGit(screen: String, ekstraParam: String = ""): Boolean {
        val mevcutUrl = webView.url ?: return false
        if (!mevcutUrl.contains("game.php")) return false
        val uri = Uri.parse(mevcutUrl)
        val village = uri.getQueryParameter("village") ?: return false
        val tParam = uri.getQueryParameter("t")?.let { "&t=$it" } ?: ""
        webView.loadUrl("${uri.scheme}://${uri.host}/game.php?village=$village&screen=$screen$ekstraParam$tParam")
        return true
    }

    private fun webViewGercekDokun(cssX: Float, cssY: Float) {
        val scale = webView.scale
        val viewX = (cssX * scale + (-2..2).random()).coerceIn(2f, (webView.width - 2).toFloat())
        val viewY = (cssY * scale + (-2..2).random()).coerceIn(2f, (webView.height - 2).toFloat())
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, viewX, viewY, 0)
        val up = MotionEvent.obtain(t, t + (40L..70L).random(), MotionEvent.ACTION_UP, viewX, viewY, 0)
        webView.dispatchTouchEvent(down)
        handler.postDelayed({ webView.dispatchTouchEvent(up); down.recycle(); up.recycle() }, 45L)
    }

    private fun onayPenceresiniTamamla(deneme: Int, etiket: String) {
        if (!aktifMi) { turDevamEdiyor = false; return }
        val js = """
            (function() {
                var btn = document.querySelector('.confirmation-box .evt-confirm-btn, .popup_box_content .evt-confirm-btn, .btn-confirm-yes');
                if (!btn || btn.offsetParent === null) return JSON.stringify({ok:false});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                handler.postDelayed({ turDevamEdiyor = false }, 1500L)
            } else if (deneme < 3) handler.postDelayed({ onayPenceresiniTamamla(deneme + 1, etiket) }, 800L)
            else turDevamEdiyor = false
        }
    }

    private fun captchaKontrolEt() {
        webView.evaluateJavascript("(function(){ return document.querySelector('#bot_check,.captcha,iframe[src*=\"captcha\"]') ? 'VAR':'YOK'; })();") { sonuc ->
            if (sonuc.contains("VAR")) {
                aktifMi = false; turDevamEdiyor = false; borsaIslemde = false
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.background = yuvarlakKutu("#2E7D32", 18f)
                durumGuncelle("🚨 BOT KORUMASI ÇIKTI! Durduruldu!", logEkle = true)
                arkaPlanServisiniDurdur()
                runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500) }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun pilKisitlamasiniKaldir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                    .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            } else durumGuncelle("✅ Pil kısıtlaması zaten kapalı")
        }
    }

    private fun arkaPlanServisiniBaslat() {
        val intent = Intent(this, BotForegroundService::class.java).apply { putExtra(BotForegroundService.EXTRA_STATUS, "Klanlar Pro Bot aktif...") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun arkaPlanServisiniDurdur() { stopService(Intent(this, BotForegroundService::class.java)) }

    private fun barbarListesiniKaydet() {
        prefs.edit().putString("harita_barbar_liste", barbarKoordinatlari.joinToString(",")).putInt("harita_barbar_index", barbarSiraIndex).apply()
        barbarDurumMetniGuncelle()
    }

    private fun barbarListesiniYukle() {
        barbarKoordinatlari.clear()
        val kayitli = prefs.getString("harita_barbar_liste", "") ?: ""
        if (kayitli.isNotEmpty()) barbarKoordinatlari.addAll(kayitli.split(",").filter { it.contains("|") })
        barbarSiraIndex = prefs.getInt("harita_barbar_index", 0)
        if (barbarSiraIndex >= barbarKoordinatlari.size) barbarSiraIndex = 0
        barbarDurumMetniGuncelle()
    }

    private fun barbarDurumMetniGuncelle() {
        val tv = findViewById<TextView>(R.id.tvHaritaBarbarDurum)
        tv.text = if (barbarKoordinatlari.isEmpty()) "Kayıtlı barbar yok"
        else "Barbar: ${barbarKoordinatlari.size} köy | Sıradaki: ${barbarSiraIndex + 1} (${barbarKoordinatlari[barbarSiraIndex % barbarKoordinatlari.size]})"
    }

    private fun kuyruguKaydet(tvKuyruk: TextView) {
        tvKuyruk.text = if (binaKuyrugu.isEmpty()) "Sıra boş" else binaKuyrugu.mapIndexed { i, g -> "${i + 1}. ${g.ad} ➔ Sv.${g.hedefSeviye}" }.joinToString("\n")
        prefs.edit().putString("bina_kuyruk_v2", binaKuyrugu.joinToString(";") { "${it.kod}|${it.ad}|${it.hedefSeviye}" }).apply()
    }

    private fun kuyruguYukle(tvKuyruk: TextView) {
        binaKuyrugu.clear()
        val data = prefs.getString("bina_kuyruk_v2", "") ?: ""
        if (data.isNotEmpty()) data.split(";").forEach { item ->
            val p = item.split("|")
            if (p.size == 3) (p[2].toIntOrNull() ?: 0).takeIf { it > 0 }?.let { binaKuyrugu.add(BinaGorevi(p[0], p[1], it)) }
        }
        kuyruguKaydet(tvKuyruk)
    }

    private fun parseJsJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { JSONObject(raw.trim('"').replace("\\\"", "\"")) }.getOrNull()
    }

    inner class BotJsBridge {
        @JavascriptInterface fun onBorsaBos(stokBilgisi: String) {
            handler.post {
                durumGuncelle("[Borsa] $stokBilgisi")
                borsaIslemde = false
            }
        }
        @JavascriptInterface fun onBorsaHizSiniri() {
            handler.post {
                borsaHizSiniriBitisZamani = System.currentTimeMillis() + 15_000L
                durumGuncelle("[Borsa] 🛑 Sunucu yoğunluk uyarısı! 15 sn mola verildi...", logEkle = true)
                borsaKutulariniTemizle()
                borsaIslemde = false
            }
        }
        @JavascriptInterface fun onBorsaTetiklendi(res: String, adet: Int, kur: Int) {
            handler.post {
                if (!aktifMi) { borsaIslemde = false; return@post }
                sonDeneneKaynak = res
                val trAd = when (res) { "wood" -> "Odun"; "stone" -> "Kil"; "iron" -> "Demir"; else -> res }
                durumGuncelle("[Borsa] ⚡ $trAd ($adet | Oran: $kur) çekiliyor!", logEkle = true)
            }
        }
        @JavascriptInterface fun onBorsaOnayDokun(onayMi: Boolean, x: Float, y: Float) {
            handler.post {
                if (!aktifMi) return@post
                if (x >= 0f && y >= 0f) webViewGercekDokun(x, y)
                if (onayMi) {
                    durumGuncelle("[Borsa] ✅ Onayla basıldı!", logEkle = true)
                } else {
                    basarisizKaynak = sonDeneneKaynak
                    basarisizKaynakZamani = System.currentTimeMillis()
                    durumGuncelle("[Borsa] ⚠️ Kapıldı, iptal edildi...")
                }
                handler.postDelayed({ borsaKutulariniTemizle(); borsaIslemde = false }, 220L)
            }
        }
        @JavascriptInterface fun onBorsaSonuc(basariliMi: Boolean) {
            handler.post {
                if (basariliMi) {
                    durumGuncelle("[Borsa] ✅ Anında çekildi!", logEkle = true)
                } else {
                    basarisizKaynak = sonDeneneKaynak
                    basarisizKaynakZamani = System.currentTimeMillis()
                    durumGuncelle("[Borsa] ⚠️ Kapıldı, sıradaki madene geçiliyor...")
                }
                handler.postDelayed({ borsaKutulariniTemizle(); borsaIslemde = false }, 180L)
            }
        }
    }

    override fun onPause() { super.onPause(); ayarlariKaydet() }
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
