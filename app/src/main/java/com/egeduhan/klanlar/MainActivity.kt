package com.egeduhan.klanlar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.text.InputType
import android.view.Gravity
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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var webView: BackgroundWebView
    private lateinit var tvDurum: TextView
    private lateinit var btnBaslat: Button
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
    private var borsaHizSiniriBitisZamani = 0L

    // Başkası kaptığında o kaynağı 2 sn atlamak için
    private var sonDeneneKaynak = ""
    private var basarisizKaynak = ""
    private var basarisizKaynakZamani = 0L

    // Okçusuz dünya birimleri
    private val askerBirimleri = listOf(
        "spear" to "Mızrakçı",
        "sword" to "Kılıç ustası",
        "axe" to "Baltacı",
        "spy" to "Casus",
        "light" to "Hafif atlı",
        "heavy" to "Ağır atlı"
    )
    private var seciliBirimIndex = 0

    // Harita Barbar Yağma Değişkenleri
    private var haritaYagmaBirimIndex = 4 // Varsayılan: Hafif atlı
    private val barbarKoordinatlari = mutableListOf<String>()
    private var barbarSiraIndex = 0
    private lateinit var cbHaritaYagmaAktif: CheckBox
    private lateinit var etHaritaMesafe: EditText
    private lateinit var etHaritaAskerAdet: EditText
    private lateinit var tvHaritaBirim: TextView
    private lateinit var tvHaritaBarbarDurum: TextView

    private var yagmaSablonu = "A"
    private var borsaSatisModu = false

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
    private var seciliBinaIndex = 10

    data class BinaGorevi(val kod: String, val ad: String, val hedefSeviye: Int)
    private val binaKuyrugu = mutableListOf<BinaGorevi>()

    private val prefs by lazy { getSharedPreferences("klanlar_desktop_bot", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        tvDurum = findViewById(R.id.tvDurum)
        btnBaslat = findViewById(R.id.btnBaslatDurdur)

        bildirimIzniIste()
        tarayiciyiHazirla()
        paneliKur()
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
                if (!v.hasFocus()) {
                    v.requestFocus()
                }
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
            webView.requestFocus()
        }
        btnFloatingIcon.setOnClickListener {
            btnFloatingIcon.visibility = View.GONE
            mainPanel.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnPilIzni).setOnClickListener {
            pilKisitlamasiniKaldir()
        }

        findViewById<Button>(R.id.btnSayfaYenile).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnKoyGenel).setOnClickListener { ekranaGit("overview") }
        findViewById<CheckBox>(R.id.cbEkranAcikTut).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

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

        val btnYagmaSablon = findViewById<Button>(R.id.btnYagmaSablon)
        btnYagmaSablon.setOnClickListener {
            yagmaSablonu = if (yagmaSablonu == "A") "B" else "A"
            btnYagmaSablon.text = yagmaSablonu
        }

        haritaBarbarArayuzuKur(findViewById(R.id.layoutYagma))

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

        val etBorsaSaniye = findViewById<EditText>(R.id.etBorsaSaniye)
        etBorsaSaniye.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etBorsaSaniye.setText("1.2")

        val btnBorsaMod = findViewById<Button>(R.id.btnBorsaMod)
        btnBorsaMod.text = "MOD: AL (HAM)"
        btnBorsaMod.setOnClickListener {
            borsaSatisModu = !borsaSatisModu
            btnBorsaMod.text = if (borsaSatisModu) "MOD: SAT (PP)" else "MOD: AL (HAM)"
        }

        btnBaslat.setOnClickListener {
            aktifMi = !aktifMi
            if (aktifMi) {
                btnBaslat.text = "SİSTEMİ DURDUR"
                btnBaslat.setBackgroundColor(Color.parseColor("#A83228"))
                durumGuncelle("Otomasyon başlatıldı")
                arkaPlanServisiniBaslat()
                sonAskerZamani = 0L
                sonYagmaZamani = 0L
                sonBinaZamani = 0L
                sonTemizlikZamani = 0L
                sonBorsaZamani = 0L
                borsaHizSiniriBitisZamani = 0L
                basarisizKaynak = ""
                turDevamEdiyor = false
                borsaIslemde = false
                anaDonguyuBaslat(tvBinaKuyrugu)
            } else {
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.setBackgroundColor(Color.parseColor("#4B7A24"))
                durumGuncelle("Durduruldu")
                arkaPlanServisiniDurdur()
                turDevamEdiyor = false
                borsaIslemde = false
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    private fun haritaBarbarArayuzuKur(layoutYagma: LinearLayout) {
        val ayrac = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 12, 0, 12)
            }
            setBackgroundColor(Color.parseColor("#6E4B2A"))
        }
        layoutYagma.addView(ayrac)

        cbHaritaYagmaAktif = CheckBox(this).apply {
            text = "Harita Barbar Yağması Aktif"
            setTextColor(Color.parseColor("#F5E6C8"))
            textSize = 13f
        }
        layoutYagma.addView(cbHaritaYagmaAktif)

        val satir1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 6, 0, 6)
            }
        }

        val lblMesafe = TextView(this).apply {
            text = "Mesafe: "
            setTextColor(Color.parseColor("#F5E6C8"))
            textSize = 12f
        }
        etHaritaMesafe = EditText(this).apply {
            setText("12")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A321B"))
            setPadding(14, 6, 14, 6)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 12, 0)
            }
        }

        val btnHaritaTara = Button(this).apply {
            text = "🗺️ HARİTADAN BARBAR ÇEK"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6E4B2A"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { haritadanBarbarlariTara() }
        }

        satir1.addView(lblMesafe)
        satir1.addView(etHaritaMesafe)
        satir1.addView(btnHaritaTara)
        layoutYagma.addView(satir1)

        val satir2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 4, 0, 6)
            }
        }

        val btnOnceki = Button(this).apply {
            text = "<"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A321B"))
            layoutParams = LinearLayout.LayoutParams(75, 75)
            setOnClickListener {
                haritaYagmaBirimIndex = (haritaYagmaBirimIndex - 1 + askerBirimleri.size) % askerBirimleri.size
                tvHaritaBirim.text = askerBirimleri[haritaYagmaBirimIndex].second
            }
        }

        tvHaritaBirim = TextView(this).apply {
            text = askerBirimleri[haritaYagmaBirimIndex].second
            setTextColor(Color.parseColor("#FFD27D"))
            gravity = Gravity.CENTER
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnSonraki = Button(this).apply {
            text = ">"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A321B"))
            layoutParams = LinearLayout.LayoutParams(75, 75).apply {
                setMargins(0, 0, 12, 0)
            }
            setOnClickListener {
                haritaYagmaBirimIndex = (haritaYagmaBirimIndex + 1) % askerBirimleri.size
                tvHaritaBirim.text = askerBirimleri[haritaYagmaBirimIndex].second
            }
        }

        val lblAdet = TextView(this).apply {
            text = "Adet: "
            setTextColor(Color.parseColor("#F5E6C8"))
            textSize = 12f
        }

        etHaritaAskerAdet = EditText(this).apply {
            setText("5")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4A321B"))
            setPadding(14, 6, 14, 6)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        satir2.addView(btnOnceki)
        satir2.addView(tvHaritaBirim)
        satir2.addView(btnSonraki)
        satir2.addView(lblAdet)
        satir2.addView(etHaritaAskerAdet)
        layoutYagma.addView(satir2)

        tvHaritaBarbarDurum = TextView(this).apply {
            setTextColor(Color.parseColor("#A9DFBF"))
            textSize = 12f
        }
        layoutYagma.addView(tvHaritaBarbarDurum)

        barbarListesiniYukle()
    }

    private fun haritadanBarbarlariTara() {
        val mevcutUrl = webView.url ?: ""
        if (!mevcutUrl.contains("screen=map")) {
            durumGuncelle("[Harita] Harita ekranı açılıyor, yüklendikten sonra tekrar bas!")
            ekranaGit("map")
            return
        }

        val maxMesafe = (etHaritaMesafe.text.toString().toDoubleOrNull() ?: 12.0).coerceIn(1.0, 100.0)

        val js = """
            (function() {
                if (typeof TWMap === 'undefined' || !TWMap.villages || typeof game_data === 'undefined') {
                    return JSON.stringify({ok:false, neden:'harita_yok'});
                }
                var myX = parseInt(game_data.village.x, 10);
                var myY = parseInt(game_data.village.y, 10);
                var maxM = $maxMesafe;
                var bulunanlar = [];

                Object.keys(TWMap.villages).forEach(function(key) {
                    var v = TWMap.villages[key];
                    if (!v) return;
                    var isBarbar = (v.owner === 0 || v.owner === '0' || v.owner === false || v.owner === null);
                    if (!isBarbar) return;

                    var xy = parseInt(v.xy || key, 10);
                    if (isNaN(xy)) return;
                    var vx = Math.floor(xy / 1000);
                    var vy = xy % 1000;
                    if (vx === myX && vy === myY) return;

                    var dist = Math.hypot(vx - myX, vy - myY);
                    if (dist <= maxM) {
                        bulunanlar.push({coord: vx + '|' + vy, dist: dist});
                    }
                });

                bulunanlar.sort(function(a, b) { return a.dist - b.dist; });
                var tekil = [];
                var gorulen = {};
                bulunanlar.forEach(function(item) {
                    if (!gorulen[item.coord]) {
                        gorulen[item.coord] = true;
                        tekil.push(item.coord);
                    }
                });

                return JSON.stringify({ok:true, merkez: myX + '|' + myY, liste: tekil});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                val arr: JSONArray = obj.optJSONArray("liste") ?: JSONArray()
                barbarKoordinatlari.clear()
                for (i in 0 until arr.length()) {
                    barbarKoordinatlari.add(arr.getString(i))
                }
                barbarSiraIndex = 0
                barbarListesiniKaydet()
                val merkez = obj.optString("merkez")
                durumGuncelle("[Harita] ✅ $merkez çevresinde ${barbarKoordinatlari.size} barbar bulundu!")
            } else {
                durumGuncelle("[Harita] ⚠️ Harita verisi okunamadı, haritayı biraz kaydırıp tekrar dene")
            }
        }
    }

    private fun barbarListesiniKaydet() {
        prefs.edit()
            .putString("harita_barbar_liste", barbarKoordinatlari.joinToString(","))
            .putInt("harita_barbar_index", barbarSiraIndex)
            .apply()
        barbarDurumMetniGuncelle()
    }

    private fun barbarListesiniYukle() {
        barbarKoordinatlari.clear()
        val kayitli = prefs.getString("harita_barbar_liste", "") ?: ""
        if (kayitli.isNotEmpty()) {
            barbarKoordinatlari.addAll(kayitli.split(",").filter { it.contains("|") })
        }
        barbarSiraIndex = prefs.getInt("harita_barbar_index", 0)
        if (barbarSiraIndex >= barbarKoordinatlari.size) barbarSiraIndex = 0
        barbarDurumMetniGuncelle()
    }

    private fun barbarDurumMetniGuncelle() {
        if (barbarKoordinatlari.isEmpty()) {
            tvHaritaBarbarDurum.text = "Kayıtlı barbar yok (Haritayı açıp 'Barbar Çek' bas)"
        } else {
            val siradaki = barbarKoordinatlari[barbarSiraIndex % barbarKoordinatlari.size]
            tvHaritaBarbarDurum.text = "Kayıtlı Barbar: ${barbarKoordinatlari.size} köy | Sıradaki: ${barbarSiraIndex + 1} ($siradaki)"
        }
    }

    @SuppressLint("BatteryLife")
    private fun pilKisitlamasiniKaldir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }.onFailure {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                durumGuncelle("✅ Pil kısıtlaması zaten kaldırılmış!")
            }
        }
    }

    private fun arkaPlanServisiniBaslat() {
        val intent = Intent(this, BotForegroundService::class.java).apply {
            putExtra(BotForegroundService.EXTRA_STATUS, "Klanlar Bot arka planda çalışıyor...")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun arkaPlanServisiniDurdur() {
        stopService(Intent(this, BotForegroundService::class.java))
    }

    private fun durumGuncelle(metin: String) {
        tvDurum.text = "Durum: $metin"
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
                val borsaAktif = findViewById<CheckBox>(R.id.cbBorsaAktif).isChecked

                // Kilitlenme koruması
                if (borsaIslemde && (simdi - borsaIslemBaslangic > 1800L)) {
                    borsaIslemde = false
                }

                // 1) BORSA DÖNGÜSÜ (Güvenli 1.2 sn + açık onay penceresi kontrolü)
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

                // 2) DİĞER MODÜLLER (Asker, Yağma, Bina, Temizlik)
                if (!turDevamEdiyor && !borsaIslemde) {
                    val askerAktif = findViewById<CheckBox>(R.id.cbAskerAktif).isChecked
                    val yagmaAktif = findViewById<CheckBox>(R.id.cbYagmaAktif).isChecked
                    val haritaYagmaAktif = cbHaritaYagmaAktif.isChecked
                    val binaAktif = findViewById<CheckBox>(R.id.cbBinaAktif).isChecked
                    val temizlikAktif = findViewById<CheckBox>(R.id.cbTemizlikAktif).isChecked

                    val askerSure = (findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L).coerceAtLeast(1L) * 60_000L
                    val yagmaSure = (findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L).coerceAtLeast(1L) * 60_000L
                    val binaSure = (findViewById<EditText>(R.id.etBinaDakika).text.toString().toLongOrNull() ?: 4L).coerceAtLeast(1L) * 60_000L
                    val temizlikSure = (findViewById<EditText>(R.id.etTemizlikDakika).text.toString().toLongOrNull() ?: 20L).coerceAtLeast(1L) * 60_000L

                    when {
                        askerAktif && (simdi - sonAskerZamani >= askerSure) -> {
                            sonAskerZamani = simdi
                            askerTurunuCalistir()
                        }
                        haritaYagmaAktif && barbarKoordinatlari.isNotEmpty() && (simdi - sonYagmaZamani >= yagmaSure) -> {
                            sonYagmaZamani = simdi
                            haritaBarbarYagmaBaslat()
                        }
                        yagmaAktif && (simdi - sonYagmaZamani >= yagmaSure) -> {
                            sonYagmaZamani = simdi
                            yagmaTurunuCalistir()
                        }
                        binaAktif && binaKuyrugu.isNotEmpty() && (simdi - sonBinaZamani >= binaSure) -> {
                            sonBinaZamani = simdi
                            binaTurunuCalistir(tvBinaKuyrugu)
                        }
                        temizlikAktif && (simdi - sonTemizlikZamani >= temizlikSure) -> {
                            sonTemizlikZamani = simdi
                            temizlikTurunuCalistir()
                        }
                    }
                }
                handler.postDelayed(this, 180L)
            }
        }
        handler.postDelayed(dongu, 200L)
    }

    private fun ekranaGit(screen: String, ekstraParam: String = ""): Boolean {
        val mevcutUrl = webView.url ?: return false
        if (!mevcutUrl.contains("game.php")) {
            durumGuncelle("⚠️ Önce dünyaya giriş yap!")
            return false
        }
        val uri = Uri.parse(mevcutUrl)
        val base = "${uri.scheme}://${uri.host}/game.php"
        val village = uri.getQueryParameter("village") ?: return false
        val tParam = uri.getQueryParameter("t")?.let { "&t=$it" } ?: ""
        val hedefUrl = "$base?village=$village&screen=$screen$ekstraParam$tParam"
        webView.loadUrl(hedefUrl)
        return true
    }

    private fun webViewGercekDokun(cssX: Float, cssY: Float) {
        val scale = webView.scale
        val viewX = (cssX * scale + (-2..2).random()).coerceIn(2f, (webView.width - 2).toFloat())
        val viewY = (cssY * scale + (-2..2).random()).coerceIn(2f, (webView.height - 2).toFloat())

        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, viewX, viewY, 0)
        val upEvent = MotionEvent.obtain(downTime, downTime + (40L..70L).random(), MotionEvent.ACTION_UP, viewX, viewY, 0)

        webView.dispatchTouchEvent(downEvent)
        handler.postDelayed({
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
        }, 45L)
    }

    // --- HARİTADAN TOPLANAN BARBARLARI YAĞMALA ---
    private fun haritaBarbarYagmaBaslat() {
        if (barbarKoordinatlari.isEmpty()) {
            durumGuncelle("[Harita Yağma] Kayıtlı barbar yok!")
            return
        }
        turDevamEdiyor = true
        haritaSiradakiBarbaraSaldir()
    }

    private fun haritaSiradakiBarbaraSaldir() {
        if (!aktifMi || !cbHaritaYagmaAktif.isChecked || barbarKoordinatlari.isEmpty()) {
            turDevamEdiyor = false
            return
        }

        val koordinat = barbarKoordinatlari[barbarSiraIndex % barbarKoordinatlari.size]
        val parcalar = koordinat.split("|")
        if (parcalar.size != 2) {
            barbarSiraIndex = (barbarSiraIndex + 1) % barbarKoordinatlari.size
            barbarListesiniKaydet()
            turDevamEdiyor = false
            return
        }

        val hedefX = parcalar[0]
        val hedefY = parcalar[1]
        val (birimKod, birimAd) = askerBirimleri[haritaYagmaBirimIndex]
        val adet = (etHaritaAskerAdet.text.toString().toIntOrNull() ?: 5).coerceIn(1, 5000)

        durumGuncelle("[Harita Yağma] İçtima Meydanı açılıyor ($koordinat)...")
        if (!ekranaGit("place")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val jsForm = """
                (function() {
                    var unitInp = document.querySelector('input[name="$birimKod"]');
                    var xInp = document.querySelector('input[name="x"]');
                    var yInp = document.querySelector('input[name="y"]');
                    var btn = document.getElementById('target_attack');
                    if (!unitInp || !xInp || !yInp || !btn) return JSON.stringify({ok:false, neden:'form_yok'});

                    var link = document.querySelector('a.units-entry-all[data-unit="$birimKod"]');
                    var mevcut = 0;
                    if (link) {
                        var m = (link.innerText || '').match(/(\d+)/);
                        if (m) mevcut = parseInt(m[1], 10);
                    }
                    if (mevcut < $adet) return JSON.stringify({ok:false, neden:'asker_yok', mevcut:mevcut});

                    unitInp.focus();
                    unitInp.value = '$adet';
                    unitInp.dispatchEvent(new Event('input', {bubbles:true}));

                    xInp.value = '$hedefX';
                    xInp.dispatchEvent(new Event('input', {bubbles:true}));
                    yInp.value = '$hedefY';
                    yInp.dispatchEvent(new Event('input', {bubbles:true}));

                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()

            webView.evaluateJavascript(jsForm) { sonuc ->
                val obj = parseJsJson(sonuc)
                if (obj != null && obj.optBoolean("ok")) {
                    durumGuncelle("[Harita Yağma] 🐎 $koordinat ($adet $birimAd) gönderiliyor...")
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({
                            haritaSaldiriOnayla(koordinat)
                        }, 2200L)
                    }, 500L)
                } else {
                    val neden = obj?.optString("neden") ?: ""
                    if (neden == "asker_yok") {
                        durumGuncelle("[Harita Yağma] Yeterli $birimAd kalmadı, tur tamamlandı.")
                    } else {
                        durumGuncelle("[Harita Yağma] İçtima formu yüklenemedi")
                    }
                    turDevamEdiyor = false
                }
            }
        }, 2600L)
    }

    private fun haritaSaldiriOnayla(koordinat: String) {
        if (!aktifMi) {
            turDevamEdiyor = false
            return
        }
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
            if (obj != null && obj.optBoolean("ok")) {
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                barbarSiraIndex = (barbarSiraIndex + 1) % barbarKoordinatlari.size
                barbarListesiniKaydet()
                durumGuncelle("[Harita Yağma] ✅ $koordinat yağmaya çıkıldı!")
                handler.postDelayed({
                    haritaSiradakiBarbaraSaldir()
                }, (1600L..2300L).random())
            } else {
                durumGuncelle("[Harita Yağma] ⚠️ $koordinat onaylanamadı, sıradakine geçiliyor")
                barbarSiraIndex = (barbarSiraIndex + 1) % barbarKoordinatlari.size
                barbarListesiniKaydet()
                handler.postDelayed({
                    haritaSiradakiBarbaraSaldir()
                }, 1500L)
            }
        }
    }

    // --- 1. ASKER ÜRETİMİ ---
    private fun askerTurunuCalistir() {
        val (birimKod, birimAd) = askerBirimleri[seciliBirimIndex]
        val adet = (findViewById<EditText>(R.id.etAskerAdet).text.toString().toIntOrNull() ?: 5).coerceIn(1, 500)

        turDevamEdiyor = true
        durumGuncelle("[Asker] Kışla açılıyor ($birimAd)...")
        if (!ekranaGit("barracks")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val js = """
                (function() {
                    var inp = document.querySelector('input[name="$birimKod"]');
                    var btn = document.querySelector('input.btn-recruit');
                    if (!inp || !btn) return JSON.stringify({ok:false});
                    inp.focus();
                    inp.value = '$adet';
                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                    inp.dispatchEvent(new Event('change', {bubbles:true}));
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { sonuc ->
                val obj = parseJsJson(sonuc)
                if (obj != null && obj.optBoolean("ok")) {
                    durumGuncelle("[Asker] ⚔️ $adet $birimAd basılıyor...")
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({ turDevamEdiyor = false }, 2000L)
                    }, 700L)
                } else {
                    durumGuncelle("[Asker] ⚠️ $birimAd kutusu/hammadde uygun değil")
                    turDevamEdiyor = false
                }
            }
        }, 3000L)
    }

    // --- 2. YAĞMA ASİSTANI ---
    private fun yagmaTurunuCalistir() {
        val koySayisi = (findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 15).coerceIn(1, 100)
        val sinif = if (yagmaSablonu == "A") "farm_icon_a" else "farm_icon_b"

        turDevamEdiyor = true
        durumGuncelle("[Yağma] Yağma Asistanı açılıyor...")
        if (!ekranaGit("am_farm")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            yagmaSiradakiKoyeBas(sinif, 1, koySayisi)
        }, 3000L)
    }

    private fun yagmaSiradakiKoyeBas(sinif: String, mevcut: Int, toplam: Int) {
        if (!aktifMi || mevcut > toplam) {
            turDevamEdiyor = false
            return
        }

        val js = """
            (function() {
                var btns = Array.from(document.querySelectorAll('a.$sinif:not(.farm_icon_disabled)'));
                if (btns.length === 0) return JSON.stringify({ok:false});
                var b = btns[0];
                b.scrollIntoView({block:'center'});
                var r = b.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                durumGuncelle("[Yağma] 🐎 $yagmaSablonu gönderiliyor ($mevcut/$toplam)")
                handler.postDelayed({
                    webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                    handler.postDelayed({
                        yagmaSiradakiKoyeBas(sinif, mevcut + 1, toplam)
                    }, (1300L..1950L).random())
                }, 400L)
            } else {
                durumGuncelle("[Yağma] Uygun köy kalmadı ($mevcut/$toplam)")
                turDevamEdiyor = false
            }
        }
    }

    // --- 3. BİNA (NORMAL VEYA PREMİUM -%20 İLE) ---
    private fun binaTurunuCalistir(tvBinaKuyrugu: TextView) {
        val gorev = binaKuyrugu.firstOrNull() ?: return
        val premium20Kullan = findViewById<CheckBox>(R.id.cbBinaPremium20).isChecked

        turDevamEdiyor = true
        durumGuncelle("[Bina] Ana bina açılıyor (${gorev.ad})...")

        if (!ekranaGit("main")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val js = """
                (function() {
                    var row = document.getElementById('main_buildrow_${gorev.kod}');
                    if (!row) return JSON.stringify({durum:'yok'});
                    var text = row.innerText || '';
                    var m = text.match(/Seviye\s*(\d+)/i);
                    var sv = m ? parseInt(m[1]) : 0;
                    if (sv >= ${gorev.hedefSeviye}) return JSON.stringify({durum:'tamam', sv:sv});

                    var btn = null;
                    if ($premium20Kullan) {
                        var adaylar = Array.from(row.querySelectorAll('a, button, input'));
                        btn = adaylar.find(function(el) {
                            var t = (el.innerText || el.value || '').trim();
                            return t.indexOf('-20%') !== -1 && el.offsetParent !== null && !el.classList.contains('btn-disabled');
                        });
                    } else {
                        btn = row.querySelector('a.btn-build');
                    }

                    if (!btn || btn.offsetParent === null) return JSON.stringify({durum:'bekle', sv:sv});
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({durum:'bas', x: r.left + r.width/2, y: r.top + r.height/2, sv:sv});
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { sonuc ->
                val obj = parseJsJson(sonuc)
                when (obj?.optString("durum")) {
                    "tamam" -> {
                        durumGuncelle("[Bina] ✅ ${gorev.ad} hedef Sv.${gorev.hedefSeviye} tamam!")
                        binaKuyrugu.remove(gorev)
                        kuyruguKaydet(tvBinaKuyrugu)
                        turDevamEdiyor = false
                    }
                    "bas" -> {
                        val etiket = if (premium20Kullan) "(-%20 PP)" else ""
                        durumGuncelle("[Bina] 🔨 ${gorev.ad} $etiket yükseltiliyor...")
                        handler.postDelayed({
                            webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                            if (premium20Kullan) {
                                handler.postDelayed({ onayPenceresiniTamamla(1, "[Bina]") }, 1200L)
                            } else {
                                handler.postDelayed({ turDevamEdiyor = false }, 2000L)
                            }
                        }, 700L)
                    }
                    else -> {
                        durumGuncelle("[Bina] ⏳ ${gorev.ad} için hammadde/kuyruk bekleniyor")
                        turDevamEdiyor = false
                    }
                }
            }
        }, 3000L)
    }

    // --- 4. EŞİT SÜRELİ TEMİZLİK TURU (15 : 6 : 3 : 2 ORANI - OKÇUSUZ) ---
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

        val js = """
            (function() {
                var agirliklar = {1: 15, 2: 6, 3: 3, 4: 2};
                var kalanlar = [$kalanSeviyelerJson];
                var hedef = $hedefLv;

                var kutular = Array.from(document.querySelectorAll('.scavenge-option'));
                var hedefKutu = kutular[hedef - 1];
                if (!hedefKutu) return JSON.stringify({ok:false, neden:'kutu_yok'});

                var btn = hedefKutu.querySelector('a.free_send_button:not(.btn-disabled)');
                if (!btn) return JSON.stringify({ok:false, neden:'mesgul'});

                var toplamAgirlik = 0;
                kalanlar.forEach(function(lv) {
                    var k = kutular[lv - 1];
                    if (k && k.querySelector('a.free_send_button:not(.btn-disabled)')) {
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
                            inp.value = adet;
                            inp.dispatchEvent(new Event('input', {bubbles:true}));
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
                durumGuncelle("[Temizlik] 🧹 $hedefLv. Seviye ($adet asker) gönderiliyor...")
                handler.postDelayed({
                    webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                    handler.postDelayed({
                        temizlikSiradakiKoluGonder(seciliSeviyeler, index + 1)
                    }, 1800L)
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

    // --- 5. AÇIK PENCERE KONTROLLÜ + TABLO/AJAX BORSA ---
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

                // 1. Ekranda açık "Ticaret teklifine bak" onay penceresi varsa ÖNCE ONU ONAYLA!
                function acikPencereyiIsle() {
                    var kutular = Array.from(document.querySelectorAll('.popup_box_container, .popup_box, .popup_box_content, #premium_exchange_confirm')).filter(gorunurMu);
                    for (var k = 0; k < kutular.length; k++) {
                        var adaylar = Array.from(kutular[k].querySelectorAll('button, input, a'));
                        var onayBtn = adaylar.find(function(el) {
                            if (!gorunurMu(el)) return false;
                            var t = (el.innerText || el.value || '').trim().toLowerCase();
                            return el.classList.contains('evt-confirm-btn') || el.classList.contains('btn-confirm-yes') || t === 'onayla' || t === 'confirm';
                        });
                        if (onayBtn) {
                            if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                            onayBtn.click();
                            AndroidBot.onBorsaSonuc(true);
                            return true;
                        }
                        var iptalBtn = adaylar.find(function(el) {
                            if (!gorunurMu(el)) return false;
                            var t = (el.innerText || el.value || '').trim().toLowerCase();
                            return el.classList.contains('evt-cancel-btn') || el.classList.contains('btn-confirm-no') || t.indexOf('iptal') !== -1 || t === 'cancel' || t === 'kapat';
                        });
                        if (iptalBtn) {
                            if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                            iptalBtn.click();
                            AndroidBot.onBorsaSonuc(false);
                            return true;
                        }
                    }
                    return false;
                }

                if (acikPencereyiIsle()) return;

                // 2. Kırmızı hata kutusu kontrolü
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

                    // Satır başlıklarından (Stok / Kapasite / Oran) yedek okuma
                    Array.from(document.querySelectorAll('#premium_exchange_form tr, table.vis tr')).forEach(function(tr) {
                        if (!tr.cells || tr.cells.length < 4) return;
                        var baslik = (tr.cells[0].innerText || '').trim().toLowerCase();
                        for (var i = 0; i < 3; i++) {
                            var val = ilkSayi(tr.cells[i + 1]);
                            if (val > 0) {
                                if (baslik === 'stok' || baslik === 'stock') res.stock[sirasi[i]] = val;
                                else if (baslik === 'kapasite' || baslik === 'capacity') res.capacity[sirasi[i]] = val;
                                else if (baslik === 'oran' || baslik === 'kur' || baslik === 'rate') res.rates[sirasi[i]] = val;
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
                    // Animasyon gecikmesi ihtimaline karşı 120ms, 300ms ve 600ms yedek kontroller
                    setTimeout(kontrol, 120);
                    setTimeout(kontrol, 300);
                    setTimeout(kontrol, 600);
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

                    ['buy_wood','buy_stone','buy_iron','sell_wood','sell_stone','sell_iron'].forEach(function(n) {
                        var el = document.querySelector('input[name="' + n + '"]');
                        if (el) el.value = '';
                    });

                    var inpName = (mod === 'sell' ? 'sell_' : 'buy_') + secilen.res;
                    var inp = document.querySelector('input[name="' + inpName + '"]');
                    var calcBtn = document.querySelector('input.btn-premium-exchange-buy, #premium_exchange_form input[type="submit"]');
                    if (!inp || !calcBtn) {
                        AndroidBot.onBorsaBos('Form bulunamadı');
                        return;
                    }

                    inp.value = secilen.adet;
                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                    inp.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true}));
                    inp.dispatchEvent(new Event('change', {bubbles:true}));

                    calcBtn.disabled = false;
                    calcBtn.classList.remove('btn-disabled');

                    anindaGozlemciBaslat();
                    AndroidBot.onBorsaTetiklendi(secilen.res, secilen.adet, secilen.kur);
                    calcBtn.click();
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
                    if (el) el.value = '';
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun onayPenceresiniTamamla(deneme: Int, etiket: String) {
        if (!aktifMi) {
            turDevamEdiyor = false
            return
        }
        val js = """
            (function() {
                var btn = document.querySelector('.popup_box_content .evt-confirm-btn, .popup_box_content .btn-confirm-yes');
                if (!btn) {
                    var adaylar = Array.from(document.querySelectorAll('.popup_box_content button, .popup_box_content input, .popup_box_content a'));
                    btn = adaylar.find(function(el) {
                        var t = (el.innerText || el.value || '').trim().toLowerCase();
                        return t === 'onayla' || t === 'confirm' || t === 'evet';
                    });
                }
                if (!btn || btn.offsetParent === null) return JSON.stringify({ok:false});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            val obj = parseJsJson(sonuc)
            if (obj != null && obj.optBoolean("ok")) {
                durumGuncelle("$etiket ✅ İşlem onaylandı!")
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                handler.postDelayed({ turDevamEdiyor = false }, 1500L)
            } else if (deneme < 3) {
                handler.postDelayed({ onayPenceresiniTamamla(deneme + 1, etiket) }, 800L)
            } else {
                turDevamEdiyor = false
            }
        }
    }

    private fun captchaKontrolEt() {
        val js = """
            (function() {
                var cap = document.querySelector('#bot_check, .captcha, iframe[src*="captcha"]');
                return cap ? 'VAR' : 'YOK';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            if (sonuc.contains("VAR")) {
                aktifMi = false
                turDevamEdiyor = false
                borsaIslemde = false
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.setBackgroundColor(Color.parseColor("#4B7A24"))
                durumGuncelle("🚨 BOT KORUMASI ÇIKTI! Sistem durduruldu!")
                arkaPlanServisiniDurdur()
                runCatching {
                    ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                }
            }
        }
    }

    private fun parseJsJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val temiz = raw.trim('"').replace("\\\"", "\"")
        return runCatching { JSONObject(temiz) }.getOrNull()
    }

    private fun kuyruguKaydet(tvKuyruk: TextView) {
        if (binaKuyrugu.isEmpty()) {
            tvKuyruk.text = "Sıra boş"
        } else {
            tvKuyruk.text = binaKuyrugu.mapIndexed { i, g -> "${i + 1}. ${g.ad} ➔ Sv.${g.hedefSeviye}" }.joinToString("\n")
        }
        val data = binaKuyrugu.joinToString(";") { "${it.kod}|${it.ad}|${it.hedefSeviye}" }
        prefs.edit().putString("bina_kuyruk_v2", data).apply()
    }

    private fun kuyruguYukle(tvKuyruk: TextView) {
        binaKuyrugu.clear()
        val data = prefs.getString("bina_kuyruk_v2", "") ?: ""
        if (data.isNotEmpty()) {
            data.split(";").forEach { item ->
                val p = item.split("|")
                if (p.size == 3) {
                    val sv = p[2].toIntOrNull() ?: 0
                    if (sv > 0) binaKuyrugu.add(BinaGorevi(p[0], p[1], sv))
                }
            }
        }
        kuyruguKaydet(tvKuyruk)
    }

    inner class BotJsBridge {
        @JavascriptInterface
        fun onBorsaBos(stokBilgisi: String) {
            handler.post {
                durumGuncelle("[Borsa] $stokBilgisi")
                borsaIslemde = false
            }
        }

        @JavascriptInterface
        fun onBorsaHizSiniri() {
            handler.post {
                borsaHizSiniriBitisZamani = System.currentTimeMillis() + 15_000L
                durumGuncelle("[Borsa] 🛑 Sunucu yoğunluk uyarısı! 15 sn mola verildi...")
                borsaKutulariniTemizle()
                borsaIslemde = false
            }
        }

        @JavascriptInterface
        fun onBorsaTetiklendi(res: String, adet: Int, kur: Int) {
            handler.post {
                if (!aktifMi) {
                    borsaIslemde = false
                    return@post
                }
                sonDeneneKaynak = res
                val trAd = when (res) {
                    "wood" -> "Odun"
                    "stone" -> "Kil"
                    "iron" -> "Demir"
                    else -> res
                }
                durumGuncelle("[Borsa] ⚡ $trAd ($adet | Oran: $kur) çekiliyor!")
            }
        }

        @JavascriptInterface
        fun onBorsaSonuc(basariliMi: Boolean) {
            handler.post {
                if (basariliMi) {
                    durumGuncelle("[Borsa] ✅ Anında çekildi!")
                } else {
                    basarisizKaynak = sonDeneneKaynak
                    basarisizKaynakZamani = System.currentTimeMillis()
                    durumGuncelle("[Borsa] ⚠️ Kapıldı, sıradaki madene geçiliyor...")
                }
                handler.postDelayed({
                    borsaKutulariniTemizle()
                    borsaIslemde = false
                }, 150L)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
