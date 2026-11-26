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

    private var yagmaSablonu = "A"
    private var borsaSatisModu = true

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
        btnBorsaMod.setOnClickListener {
            borsaSatisModu = !borsaSatisModu
            btnBorsaMod.text = if (borsaSatisModu) "MOD: SAT (PP)" else "MOD: AL (HAM)"
        }

        btnBaslat.setOnClickListener {
            aktifMi = !aktifMi
            if (aktifMi) {
                btnBaslat.text = "SİSTEMİ DURDUR"
                btnBaslat.setBackgroundColor(Color.parseColor("#A83228"))
                tvDurum.text = "Durum: Otomasyon başlatıldı..."
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
                tvDurum.text = "Durum: Durduruldu"
                turDevamEdiyor = false
                handler.removeCallbacksAndMessages(null)
            }
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

                    val borsaSure = (findViewById<EditText>(R.id.etBorsaSaniye).text.toString().toLongOrNull() ?: 30L).coerceAtLeast(10L) * 1000L
                    val askerSure = (findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L).coerceAtLeast(1L) * 60_000L
                    val yagmaSure = (findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L).coerceAtLeast(1L) * 60_000L
                    val binaSure = (findViewById<EditText>(R.id.etBinaDakika).text.toString().toLongOrNull() ?: 4L).coerceAtLeast(1L) * 60_000L
                    val temizlikSure = (findViewById<EditText>(R.id.etTemizlikDakika).text.toString().toLongOrNull() ?: 30L).coerceAtLeast(1L) * 60_000L

                    when {
                        borsaAktif && (simdi - sonBorsaZamani >= borsaSure) -> {
                            sonBorsaZamani = simdi
                            borsaTurunuCalistir()
                        }
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
                        else -> {
                            tvDurum.text = "Durum: Sıradaki tur bekleniyor..."
                        }
                    }
                }
                handler.postDelayed(this, 1500L)
            }
        }
        handler.postDelayed(dongu, 1000L)
    }

    private fun ekranaGit(screen: String, ekstraParam: String = ""): Boolean {
        val mevcutUrl = webView.url ?: return false
        if (!mevcutUrl.contains("game.php")) {
            tvDurum.text = "⚠️ Önce dünyaya giriş yap!"
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
        val viewX = (cssX * scale + (-3..3).random()).coerceIn(2f, (webView.width - 2).toFloat())
        val viewY = (cssY * scale + (-3..3).random()).coerceIn(2f, (webView.height - 2).toFloat())

        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, viewX, viewY, 0)
        val upEvent = MotionEvent.obtain(downTime, downTime + (55L..95L).random(), MotionEvent.ACTION_UP, viewX, viewY, 0)

        webView.dispatchTouchEvent(downEvent)
        handler.postDelayed({
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
        }, 65L)
    }

    private fun askerTurunuCalistir() {
        val (birimKod, birimAd) = askerBirimleri[seciliBirimIndex]
        val adet = (findViewById<EditText>(R.id.etAskerAdet).text.toString().toIntOrNull() ?: 5).coerceIn(1, 500)

        turDevamEdiyor = true
        tvDurum.text = "[Asker] Kışla açılıyor ($birimAd)..."
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
                val temiz = sonuc.trim('"').replace("\\\"", "\"")
                val obj = runCatching { JSONObject(temiz) }.getOrNull()
                if (obj != null && obj.optBoolean("ok")) {
                    tvDurum.text = "[Asker] ⚔️ $adet $birimAd basılıyor..."
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({ turDevamEdiyor = false }, 2200L)
                    }, 900L)
                } else {
                    tvDurum.text = "[Asker] ⚠️ $birimAd kutusu/hammadde uygun değil"
                    turDevamEdiyor = false
                }
            }
        }, 3200L)
    }

    private fun yagmaTurunuCalistir() {
        val koySayisi = (findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 15).coerceIn(1, 100)
        val sinif = if (yagmaSablonu == "A") "farm_icon_a" else "farm_icon_b"

        turDevamEdiyor = true
        tvDurum.text = "[Yağma] Yağma Asistanı açılıyor..."
        if (!ekranaGit("am_farm")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            yagmaSiradakiKoyeBas(sinif, 1, koySayisi)
        }, 3200L)
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
            val temiz = sonuc.trim('"').replace("\\\"", "\"")
            val obj = runCatching { JSONObject(temiz) }.getOrNull()
            if (obj != null && obj.optBoolean("ok")) {
                tvDurum.text = "[Yağma] 🐎 $yagmaSablonu gönderiliyor ($mevcut/$toplam)"
                handler.postDelayed({
                    webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                    handler.postDelayed({
                        yagmaSiradakiKoyeBas(sinif, mevcut + 1, toplam)
                    }, (1350L..2100L).random())
                }, 450L)
            } else {
                tvDurum.text = "[Yağma] Uygun köy kalmadı ($mevcut/$toplam)"
                turDevamEdiyor = false
            }
        }
    }

    private fun binaTurunuCalistir(tvBinaKuyrugu: TextView) {
        val gorev = binaKuyrugu.firstOrNull() ?: return
        turDevamEdiyor = true
        tvDurum.text = "[Bina] Ana bina açılıyor (${gorev.ad})..."

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
                    var btn = row.querySelector('a.btn-build');
                    if (!btn || btn.offsetParent === null) return JSON.stringify({durum:'bekle', sv:sv});
                    btn.scrollIntoView({block:'center'});
                    var r = btn.getBoundingClientRect();
                    return JSON.stringify({durum:'bas', x: r.left + r.width/2, y: r.top + r.height/2, sv:sv});
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { sonuc ->
                val temiz = sonuc.trim('"').replace("\\\"", "\"")
                val obj = runCatching { JSONObject(temiz) }.getOrNull()
                when (obj?.optString("durum")) {
                    "tamam" -> {
                        tvDurum.text = "[Bina] ✅ ${gorev.ad} hedef Sv.${gorev.hedefSeviye} tamam!"
                        binaKuyrugu.remove(gorev)
                        kuyruguKaydet(tvBinaKuyrugu)
                        turDevamEdiyor = false
                    }
                    "bas" -> {
                        tvDurum.text = "[Bina] 🔨 ${gorev.ad} yükseltiliyor..."
                        handler.postDelayed({
                            webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                            handler.postDelayed({ turDevamEdiyor = false }, 2200L)
                        }, 800L)
                    }
                    else -> {
                        tvDurum.text = "[Bina] ⏳ ${gorev.ad} için hammadde/kuyruk bekleniyor"
                        turDevamEdiyor = false
                    }
                }
            }
        }, 3200L)
    }

    private fun temizlikTurunuCalistir() {
        turDevamEdiyor = true
        tvDurum.text = "[Temizlik] İçtima Meydanı açılıyor..."
        if (!ekranaGit("place", "&mode=scavenge")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val js = """
                (function() {
                    var btns = Array.from(document.querySelectorAll('a.free_send_button:not(.btn-disabled)'));
                    if (btns.length === 0) return JSON.stringify({ok:false});
                    var b = btns[btns.length - 1];
                    b.scrollIntoView({block:'center'});
                    var r = b.getBoundingClientRect();
                    return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { sonuc ->
                val temiz = sonuc.trim('"').replace("\\\"", "\"")
                val obj = runCatching { JSONObject(temiz) }.getOrNull()
                if (obj != null && obj.optBoolean("ok")) {
                    tvDurum.text = "[Temizlik] 🧹 Temizlik başlatılıyor..."
                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({ turDevamEdiyor = false }, 2200L)
                    }, 800L)
                } else {
                    tvDurum.text = "[Temizlik] Boşta temizlik kolu yok"
                    turDevamEdiyor = false
                }
            }
        }, 3200L)
    }

    private fun borsaTurunuCalistir() {
        val odunAktif = findViewById<CheckBox>(R.id.cbBorsaOdun).isChecked
        val kilAktif = findViewById<CheckBox>(R.id.cbBorsaKil).isChecked
        val demirAktif = findViewById<CheckBox>(R.id.cbBorsaDemir).isChecked

        val kaynaklar = mutableListOf<String>()
        if (odunAktif) kaynaklar.add("wood")
        if (kilAktif) kaynaklar.add("stone")
        if (demirAktif) kaynaklar.add("iron")
        if (kaynaklar.isEmpty()) return

        val miktar = (findViewById<EditText>(R.id.etBorsaMiktar).text.toString().toIntOrNull() ?: 1000).coerceAtLeast(1)
        val kurLimit = (findViewById<EditText>(R.id.etBorsaKurLimit).text.toString().toIntOrNull() ?: 60).coerceAtLeast(1)
        val mod = if (borsaSatisModu) "sell" else "buy"

        turDevamEdiyor = true
        tvDurum.text = "[Borsa] Premium Takası kontrol ediliyor..."

        if (!ekranaGit("market", "&mode=exchange")) {
            turDevamEdiyor = false
            return
        }

        handler.postDelayed({
            if (!aktifMi) return@postDelayed
            val kaynakJson = kaynaklar.joinToString(",") { "'$it'" }
            val js = """
                (function() {
                    var hedefler = [$kaynakJson];
                    var mod = '$mod';
                    var miktar = $miktar;
                    var limit = $kurLimit;

                    for (var i = 0; i < hedefler.length; i++) {
                        var res = hedefler[i];
                        var stokEl = document.getElementById('premium_exchange_stock_' + res);
                        var kapEl = document.getElementById('premium_exchange_capacity_' + res);
                        var kurEl = document.getElementById('premium_exchange_rate_' + res);
                        if (!stokEl || !kapEl || !kurEl) continue;

                        var stok = parseInt(stokEl.innerText.replace(/\./g, '')) || 0;
                        var kap = parseInt(kapEl.innerText.replace(/\./g, '')) || 0;
                        var kur = parseInt(kurEl.innerText.replace(/\./g, '')) || 0;

                        if (mod === 'sell') {
                            var bosYer = kap - stok;
                            if (bosYer >= 1 && kur <= limit) {
                                var satilacak = Math.min(miktar, bosYer);
                                var inp = document.querySelector('input[name="sell_' + res + '"]');
                                var calcBtn = document.querySelector('input.btn-premium-exchange-buy, #premium_exchange_form input[type="submit"]');
                                if (inp && calcBtn) {
                                    inp.value = satilacak;
                                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                                    calcBtn.scrollIntoView({block:'center'});
                                    var r = calcBtn.getBoundingClientRect();
                                    return JSON.stringify({ok:true, res:res, adet:satilacak, kur:kur, x: r.left + r.width/2, y: r.top + r.height/2});
                                }
                            }
                        } else {
                            if (stok >= 1 && kur >= limit) {
                                var alinacak = Math.min(miktar, stok);
                                var inp = document.querySelector('input[name="buy_' + res + '"]');
                                var calcBtn = document.querySelector('input.btn-premium-exchange-buy, #premium_exchange_form input[type="submit"]');
                                if (inp && calcBtn) {
                                    inp.value = alinacak;
                                    inp.dispatchEvent(new Event('input', {bubbles:true}));
                                    calcBtn.scrollIntoView({block:'center'});
                                    var r = calcBtn.getBoundingClientRect();
                                    return JSON.stringify({ok:true, res:res, adet:alinacak, kur:kur, x: r.left + r.width/2, y: r.top + r.height/2});
                                }
                            }
                        }
                    }
                    return JSON.stringify({ok:false});
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) { sonuc ->
                val temiz = sonuc.trim('"').replace("\\\"", "\"")
                val obj = runCatching { JSONObject(temiz) }.getOrNull()
                if (obj != null && obj.optBoolean("ok")) {
                    val res = obj.optString("res")
                    val adet = obj.optInt("adet")
                    val kur = obj.optInt("kur")
                    tvDurum.text = "[Borsa] 💰 $res ($adet adet | Oran: $kur) hesaplanıyor..."

                    handler.postDelayed({
                        webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                        handler.postDelayed({
                            borsaOnayPenceresiniTamamla()
                        }, 1600L)
                    }, 700L)
                } else {
                    tvDurum.text = "[Borsa] Uygun stok/oran bulunamadı"
                    turDevamEdiyor = false
                }
            }
        }, 3000L)
    }

    private fun borsaOnayPenceresiniTamamla() {
        val js = """
            (function() {
                var btn = document.querySelector('#premium_exchange_confirm button.evt-confirm-btn, .popup_box_content button.btn-confirm-yes');
                if (!btn) return JSON.stringify({ok:false});
                var r = btn.getBoundingClientRect();
                return JSON.stringify({ok:true, x: r.left + r.width/2, y: r.top + r.height/2});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { sonuc ->
            val temiz = sonuc.trim('"').replace("\\\"", "\"")
            val obj = runCatching { JSONObject(temiz) }.getOrNull()
            if (obj != null && obj.optBoolean("ok")) {
                tvDurum.text = "[Borsa] ✅ İşlem onaylanıyor!"
                webViewGercekDokun(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
            }
            handler.postDelayed({ turDevamEdiyor = false }, 1800L)
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
                tvDurum.text = "🚨 BOT KORUMASI ÇIKTI! Sistem durduruldu!"
                runCatching {
                    ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                }
            }
        }
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
        fun log(mesaj: String) {
            handler.post { tvDurum.text = mesaj }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
