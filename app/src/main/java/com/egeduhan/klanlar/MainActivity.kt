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

    private lateinit var webView: BackgroundWebView
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
                durumGuncelle("Otomasyon başlatıldı (Arka plan koruması aktif)")
                arkaPlanServisiniBaslat()
                sonAskerZamani = 0L
                sonYagmaZamani = 0L
                sonBinaZamani = 0L
                sonTemizlikZamani = 0L
                sonBorsaZamani = 0L
                turDevamEdiyor = false
                anaDonguyuBaslat(tvBinaKuyrugu)
            } else {
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.setBackgroundColor(Color.parseColor("#4B7A24"))
                durumGuncelle("Durduruldu")
                arkaPlanServisiniDurdur()
                turDevamEdiyor = false
                handler.removeCallbacksAndMessages(null)
            }
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

                if (!turDevamEdiyor) {
                    val simdi = System.currentTimeMillis()
                    val borsaAktif = findViewById<CheckBox>(R.id.cbBorsaAktif).isChecked
                    val askerAktif = findViewById<CheckBox>(R.id.cbAskerAktif).isChecked
                    val yagmaAktif = findViewById<CheckBox>(R.id.cbYagmaAktif).isChecked
                    val binaAktif = findViewById<CheckBox>(R.id.cbBinaAktif).isChecked
                    val temizlikAktif = findViewById<CheckBox>(R.id.cbTemizlikAktif).isChecked

                    val borsaSure = (findViewById<EditText>(R.id.etBorsaSaniye).text.toString().toLongOrNull() ?: 3L).coerceAtLeast(2L) * 1000L
                    val askerSure = (findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L).coerceAtLeast(1L) * 60_000L
                    val yagmaSure = (findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L).coerceAtLeast(1L) * 60_000L
                    val binaSure = (findViewById<EditText>(R.id.etBinaDakika).text.toString().toLongOrNull() ?: 4L).coerceAtLeast(1L) * 60_000L
                    val temizlikSure = (findViewById<EditText>(R.id.etTemizlikDakika).text.toString().toLongOrNull() ?: 20L).coerceAtLeast(1L) * 60_000L

                    when {
                        askerAktif && (simdi - sonAskerZamani >= askerSure) -> {
                            sonAskerZamani = simdi
                            askerTurunuCalistir()
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
                        borsaAktif && (simdi - sonBorsaZamani >= borsaSure) -> {
                            sonBorsaZamani = simdi
                            borsaSessizKontrolEt()
                        }
                    }
                }
                handler.postDelayed(this, 600L)
            }
        }
        handler.postDelayed(dongu, 500L)
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

                var birimler = ['spear', 'sword', 'axe', 'archer', 'light', 'marcher', 'heavy'];
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

    private fun borsaSessizKontrolEt() {
        val mevcutUrl = webView.url ?: return
        if (!mevcutUrl.contains("game.php")) {
            durumGuncelle("⚠️ Önce dünyaya giriş yap!")
            return
        }

        if (!mevcutUrl.contains("screen=market") || !mevcutUrl.contains("mode=exchange")) {
            turDevamEdiyor = true
            durumGuncelle("[Borsa] Premium Takası ekranına geçiliyor...")
            ekranaGit("market", "&mode=exchange")
            handler.postDelayed({ turDevamEdiyor = false }, 2600L)
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

        turDevamEdiyor = true
        val kaynakJson = kaynaklar.joinToString(",") { "'$it'" }

        val js = """
            (function() {
                var u = new URL(window.location.href);
                var ajaxUrl = u.pathname + '?village=' + u.searchParams.get('village') + '&screen=market&mode=exchange&ajax=exchange_data';
                if (u.searchParams.get('t')) ajaxUrl += '&t=' + u.searchParams.get('t');

                function degerCoz(obj, res, domId) {
                    if (obj && obj[res] !== undefined && obj[res] !== null) {
                        var v = obj[res];
                        if (typeof v === 'number') return Math.floor(v);
                        if (typeof v === 'string') return parseInt(v.replace(/\./g, ''), 10) || 0;
                        if (typeof v === 'object') {
                            var aday = v.amount || v.rate || v.buy || v.sell || v.value;
                            if (aday !== undefined) return parseInt(String(aday).replace(/\./g, ''), 10) || 0;
                            var vals = Object.values(v);
                            for (var k = 0; k < vals.length; k++) {
                                var n = parseInt(String(vals[k]).replace(/\./g, ''), 10);
                                if (!isNaN(n) && n > 0) return n;
                            }
                        }
                    }
                    var el = document.getElementById(domId + res);
                    if (el) return parseInt((el.innerText || '').replace(/\./g, ''), 10) || 0;
                    return 0;
                }

                fetch(ajaxUrl, {credentials: 'same-origin'})
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    var hedefler = [$kaynakJson];
                    var mod = '$mod';
                    var maxMiktar = $maxMiktar;
                    var minStok = $minStok;
                    var limit = $kurLimit;

                    var stoklar = {
                        wood: degerCoz(data && data.stock, 'wood', 'premium_exchange_stock_'),
                        stone: degerCoz(data && data.stock, 'stone', 'premium_exchange_stock_'),
                        iron: degerCoz(data && data.stock, 'iron', 'premium_exchange_stock_')
                    };
                    var kapasiteler = {
                        wood: degerCoz(data && data.capacity, 'wood', 'premium_exchange_capacity_'),
                        stone: degerCoz(data && data.capacity, 'stone', 'premium_exchange_capacity_'),
                        iron: degerCoz(data && data.capacity, 'iron', 'premium_exchange_capacity_')
                    };
                    var kurlar = {
                        wood: degerCoz(data && data.rates, 'wood', 'premium_exchange_rate_'),
                        stone: degerCoz(data && data.rates, 'stone', 'premium_exchange_rate_'),
                        iron: degerCoz(data && data.rates, 'iron', 'premium_exchange_rate_')
                    };

                    var secilen = null;
                    for (var i = 0; i < hedefler.length; i++) {
                        var res = hedefler[i];
                        var stok = stoklar[res] || 0;
                        var kap = kapasiteler[res] || 0;
                        var kur = kurlar[res] || 0;

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
                        var ozet = 'Stok: ' + stoklar.wood + '/' + stoklar.stone + '/' + stoklar.iron + ' | Kur: ' + kurlar.wood;
                        AndroidBot.onBorsaBos(ozet);
                        return;
                    }

                    var inpName = (mod === 'sell' ? 'sell_' : 'buy_') + secilen.res;
                    var inp = document.querySelector('input[name="' + inpName + '"]');
                    var calcBtn = document.querySelector('input.btn-premium-exchange-buy, #premium_exchange_form input[type="submit"]');
                    if (!inp || !calcBtn) {
                        AndroidBot.onBorsaBos('Form kutusu bulunamadı');
                        return;
                    }

                    inp.focus();
                    inp.value = secilen.adet;
                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                    inp.dispatchEvent(new Event('change', {bubbles:true}));
                    calcBtn.scrollIntoView({block:'center'});
                    var r = calcBtn.getBoundingClientRect();
                    AndroidBot.onBorsaHedefBulundu(secilen.res, secilen.adet, secilen.kur, r.left + r.width/2, r.top + r.height/2);
                })
                .catch(function() {
                    AndroidBot.onBorsaBos('Bağlantı yenileniyor');
                });
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun anindaOnayGozlemcisiKur() {
        val js = """
            (function() {
                if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                function bulVeBildir() {
                    var btn = document.querySelector('.evt-confirm-btn, .btn-confirm-yes');
                    if (!btn) {
                        var adaylar = Array.from(document.querySelectorAll('#premium_exchange_confirm button, #premium_exchange_confirm input, #premium_exchange_confirm a, .popup_box_content button, .popup_box_content input, .popup_box_content a'));
                        btn = adaylar.find(function(el) {
                            var t = (el.innerText || el.value || '').trim().toLowerCase();
                            return t === 'onayla' || t === 'confirm';
                        });
                    }
                    if (btn && btn.offsetParent !== null) {
                        if (window.__klanlarOnayObserver) window.__klanlarOnayObserver.disconnect();
                        var r = btn.getBoundingClientRect();
                        AndroidBot.onBorsaOnayHazir(r.left + r.width/2, r.top + r.height/2);
                        return true;
                    }
                    return false;
                }
                if (!bulVeBildir()) {
                    window.__klanlarOnayObserver = new MutationObserver(function() { bulVeBildir(); });
                    window.__klanlarOnayObserver.observe(document.body, {childList: true, subtree: true});
                }
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
                var btn = document.querySelector('.evt-confirm-btn, .btn-confirm-yes');
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
                turDevamEdiyor = false
            }
        }

        @JavascriptInterface
        fun onBorsaHedefBulundu(res: String, adet: Int, kur: Int, x: Float, y: Float) {
            handler.post {
                if (!aktifMi) {
                    turDevamEdiyor = false
                    return@post
                }
                durumGuncelle("[Borsa] ⚡ $res ($adet adet | Kur: $kur) çekiliyor!")
                anindaOnayGozlemcisiKur()
                webViewGercekDokun(x, y)

                handler.postDelayed({
                    if (turDevamEdiyor) turDevamEdiyor = false
                }, 2500L)
            }
        }

        @JavascriptInterface
        fun onBorsaOnayHazir(x: Float, y: Float) {
            handler.post {
                if (!aktifMi) return@post
                durumGuncelle("[Borsa] ✅ Anında onaylandı!")
                webViewGercekDokun(x, y)
                handler.postDelayed({ turDevamEdiyor = false }, 900L)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
