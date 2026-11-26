package com.egeduhan.klanlar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class KlanlarBotService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var targetPickerView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var aktifMi = false
    private var turDevamEdiyor = false

    private var sonAskerZamani = 0L
    private var sonYagmaZamani = 0L
    private var sonBinaZamani = 0L
    private var sonTemizlikZamani = 0L

    private val tumBinalar = listOf(
        "Ana Bina", "Kışla", "Ahır", "Atölye", "Gözetleme Kulesi",
        "Akademi", "Demirci", "İçtima Meydanı", "Heykel", "Pazar Yeri",
        "Oduncu", "Kil Ocağı", "Demir Madeni", "Çiftlik", "Depo",
        "Gizli Depo", "Sur"
    )
    private var seciliBinaIndex = 10 // Oduncu

    data class BinaGorevi(
        val ad: String,
        val hedefSeviye: Int
    )

    private val binaKuyrugu = mutableListOf<BinaGorevi>()

    private val prefs by lazy { getSharedPreferences("klanlar_bot_prefs", Context.MODE_PRIVATE) }

    sealed class Adim {
        data class Dokun(val x: Float, val y: Float, val bekleMs: Long, val aciklama: String) : Adim()
        data class AsagiKaydir(val bekleMs: Long, val aciklama: String) : Adim()
    }

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

    private fun kuyruguGuncelleVeKaydet(tvKuyruk: TextView) {
        if (binaKuyrugu.isEmpty()) {
            tvKuyruk.text = "Sıra boş (Bina ve hedef seviye seçip ekle)"
        } else {
            tvKuyruk.text = binaKuyrugu.mapIndexed { idx, g ->
                "${idx + 1}. ${g.ad} ➔ Hedef Sv. ${g.hedefSeviye}"
            }.joinToString("\n")
        }
        val serilestirilmis = binaKuyrugu.joinToString(";") { "${it.ad}|${it.hedefSeviye}" }
        prefs.edit().putString("bina_hedef_kuyrugu", serilestirilmis).apply()
    }

    private fun kuyruguYukle(tvKuyruk: TextView) {
        binaKuyrugu.clear()
        val kayitli = prefs.getString("bina_hedef_kuyrugu", "") ?: ""
        if (kayitli.isNotEmpty()) {
            kayitli.split(";").forEach { parca ->
                val p = parca.split("|")
                if (p.size == 2) {
                    val sv = p[1].toIntOrNull() ?: 0
                    if (sv > 0) binaKuyrugu.add(BinaGorevi(p[0], sv))
                }
            }
        }
        kuyruguGuncelleVeKaydet(tvKuyruk)
    }

    private fun arayuzuKur() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 25
            y = 170
        }

        val mainPanel = overlayView.findViewById<LinearLayout>(R.id.mainPanel)
        val btnFloatingIcon = overlayView.findViewById<Button>(R.id.btnFloatingIcon)
        val dragHeader = overlayView.findViewById<LinearLayout>(R.id.dragHeader)
        val btnMinimize = overlayView.findViewById<Button>(R.id.btnMinimize)
        val btnBaslat = overlayView.findViewById<Button>(R.id.btnBaslatDurdur)
        val tvDurum = overlayView.findViewById<TextView>(R.id.tvDurum)

        val sekmeler = listOf(
            overlayView.findViewById<Button>(R.id.tabMerkez) to overlayView.findViewById<LinearLayout>(R.id.layoutMerkez),
            overlayView.findViewById<Button>(R.id.tabAsker) to overlayView.findViewById<LinearLayout>(R.id.layoutAsker),
            overlayView.findViewById<Button>(R.id.tabYagma) to overlayView.findViewById<LinearLayout>(R.id.layoutYagma),
            overlayView.findViewById<Button>(R.id.tabBina) to overlayView.findViewById<LinearLayout>(R.id.layoutBina),
            overlayView.findViewById<Button>(R.id.tabTemizlik) to overlayView.findViewById<LinearLayout>(R.id.layoutTemizlik)
        )

        sekmeler.forEach { (btn, layout) ->
            btn.setOnClickListener {
                klavyeOdagiAc(false)
                sekmeler.forEach { (b, l) ->
                    b.setBackgroundColor(Color.parseColor("#4A321B"))
                    l.visibility = View.GONE
                }
                btn.setBackgroundColor(Color.parseColor("#6E4B2A"))
                layout.visibility = View.VISIBLE
            }
        }

        val editIds = listOf(
            R.id.etAskerDakika, R.id.etYagmaDakika, R.id.etYagmaSayi,
            R.id.etYagmaKaydirAdim, R.id.etBinaDakika, R.id.etBinaHedefSeviye,
            R.id.etTemizlikDakika
        )
        editIds.forEach { id ->
            overlayView.findViewById<EditText>(id).setOnTouchListener { v, _ ->
                klavyeOdagiAc(true)
                v.requestFocus()
                false
            }
        }

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

        // Merkez, Asker, Yağma
        overlayView.findViewById<Button>(R.id.btnNoktaOyun).setOnClickListener {
            noktaSec("oyun", "Alt menüdeki 'Oyun' simgesine dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnTestKaydir).setOnClickListener {
            asagiKaydir()
            tvDurum.text = "Test: Ekran aşağı kaydırıldı"
        }

        overlayView.findViewById<Button>(R.id.btnNoktaKisla).setOnClickListener {
            noktaSec("kisla", "Köy ekranındaki Kışla binasına dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaEgit1).setOnClickListener {
            noktaSec("egit1", "1. Birimin 'Eğit' butonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaEgit2).setOnClickListener {
            noktaSec("egit2", "2. Birimin 'Eğit' butonuna dokun", tvDurum)
        }

        overlayView.findViewById<Button>(R.id.btnNoktaYagmaAc).setOnClickListener {
            noktaSec("yagma_ac", "Yağma Asistanı ikonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaYagmaA).setOnClickListener {
            noktaSec("yagma_a", "Yağma 'A' butonuna dokun", tvDurum)
        }

        // Akıllı Bina Kontrolleri
        val tvSeciliBinaAdi = overlayView.findViewById<TextView>(R.id.tvSeciliBinaAdi)
        val etBinaHedefSeviye = overlayView.findViewById<EditText>(R.id.etBinaHedefSeviye)
        val tvBinaKuyrugu = overlayView.findViewById<TextView>(R.id.tvBinaKuyrugu)

        tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex]
        kuyruguYukle(tvBinaKuyrugu)

        overlayView.findViewById<Button>(R.id.btnBinaOnceki).setOnClickListener {
            klavyeOdagiAc(false)
            seciliBinaIndex = (seciliBinaIndex - 1 + tumBinalar.size) % tumBinalar.size
            tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex]
        }
        overlayView.findViewById<Button>(R.id.btnBinaSonraki).setOnClickListener {
            klavyeOdagiAc(false)
            seciliBinaIndex = (seciliBinaIndex + 1) % tumBinalar.size
            tvSeciliBinaAdi.text = tumBinalar[seciliBinaIndex]
        }

        overlayView.findViewById<Button>(R.id.btnNoktaAnaBina).setOnClickListener {
            noktaSec("ana_bina", "Köydeki Ana Bina'ya dokun", tvDurum)
        }

        overlayView.findViewById<Button>(R.id.btnBinaSirayaEkle).setOnClickListener {
            klavyeOdagiAc(false)
            val ad = tumBinalar[seciliBinaIndex]
            val hedefSv = (etBinaHedefSeviye.text.toString().toIntOrNull() ?: 1).coerceIn(1, 30)
            binaKuyrugu.add(BinaGorevi(ad, hedefSv))
            kuyruguGuncelleVeKaydet(tvBinaKuyrugu)
            tvDurum.text = "Sıraya eklendi: $ad ➔ Sv.$hedefSv"
        }

        overlayView.findViewById<Button>(R.id.btnBinaSirasiTemizle).setOnClickListener {
            klavyeOdagiAc(false)
            binaKuyrugu.clear()
            kuyruguGuncelleVeKaydet(tvBinaKuyrugu)
            tvDurum.text = "İnşaat sırası temizlendi"
        }

        // Temizlik Sekmesi
        overlayView.findViewById<Button>(R.id.btnNoktaIctima).setOnClickListener {
            noktaSec("ictima", "İçtima Meydanı binasına dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaTemizlikSekme).setOnClickListener {
            noktaSec("temizlik_sekme", "'Temizlik' sekmesine dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaTemizlikBaslat).setOnClickListener {
            noktaSec("temizlik_baslat", "Temizlik 'Başlat' butonuna dokun", tvDurum)
        }

        // Başlat / Durdur
        btnBaslat.setOnClickListener {
            klavyeOdagiAc(false)
            aktifMi = !aktifMi
            if (aktifMi) {
                btnBaslat.text = "SİSTEMİ DURDUR"
                btnBaslat.setBackgroundColor(Color.parseColor("#A83228"))
                tvDurum.text = "Durum: Akıllı motor aktif..."
                sonAskerZamani = 0L
                sonYagmaZamani = 0L
                sonBinaZamani = 0L
                sonTemizlikZamani = 0L
                turDevamEdiyor = false
                anaDonguyuBaslat(tvDurum, tvBinaKuyrugu)
            } else {
                btnBaslat.text = "SİSTEMİ BAŞLAT"
                btnBaslat.setBackgroundColor(Color.parseColor("#4B7A24"))
                tvDurum.text = "Durum: Durduruldu"
                turDevamEdiyor = false
                handler.removeCallbacksAndMessages(null)
            }
        }

        windowManager.addView(overlayView, overlayParams)
    }

    private fun anaDonguyuBaslat(tvDurum: TextView, tvBinaKuyrugu: TextView) {
        val kontrolGorevi = object : Runnable {
            override fun run() {
                if (!aktifMi) return

                if (!turDevamEdiyor) {
                    val simdi = System.currentTimeMillis()

                    val askerAktif = overlayView.findViewById<CheckBox>(R.id.cbAskerAktif).isChecked
                    val yagmaAktif = overlayView.findViewById<CheckBox>(R.id.cbYagmaAktif).isChecked
                    val binaAktif = overlayView.findViewById<CheckBox>(R.id.cbBinaAktif).isChecked
                    val temizlikAktif = overlayView.findViewById<CheckBox>(R.id.cbTemizlikAktif).isChecked

                    val askerSure = (overlayView.findViewById<EditText>(R.id.etAskerDakika).text.toString().toLongOrNull() ?: 7L).coerceAtLeast(1L) * 60_000L
                    val yagmaSure = (overlayView.findViewById<EditText>(R.id.etYagmaDakika).text.toString().toLongOrNull() ?: 5L).coerceAtLeast(1L) * 60_000L
                    val binaSure = (overlayView.findViewById<EditText>(R.id.etBinaDakika).text.toString().toLongOrNull() ?: 4L).coerceAtLeast(1L) * 60_000L
                    val temizlikSure = (overlayView.findViewById<EditText>(R.id.etTemizlikDakika).text.toString().toLongOrNull() ?: 30L).coerceAtLeast(1L) * 60_000L

                    when {
                        binaAktif && binaKuyrugu.isNotEmpty() && (simdi - sonBinaZamani >= binaSure) -> {
                            sonBinaZamani = simdi
                            akilliBinaTurunuBaslat(tvDurum, tvBinaKuyrugu)
                        }
                        askerAktif && (simdi - sonAskerZamani >= askerSure) -> {
                            sonAskerZamani = simdi
                            askerTurunuCalistir(tvDurum)
                        }
                        yagmaAktif && (simdi - sonYagmaZamani >= yagmaSure) -> {
                            sonYagmaZamani = simdi
                            yagmaTurunuCalistir(tvDurum)
                        }
                        temizlikAktif && (simdi - sonTemizlikZamani >= temizlikSure) -> {
                            sonTemizlikZamani = simdi
                            temizlikTurunuCalistir(tvDurum)
                        }
                        else -> {
                            tvDurum.text = "Durum: Sıradaki tur bekleniyor..."
                        }
                    }
                }

                handler.postDelayed(this, 2000L)
            }
        }
        handler.postDelayed(kontrolGorevi, 1000L)
    }

    // --- AKILLI BİNA TARAMA, KAYDIRMA VE SEVİYE YÜKSELTME MOTORU ---
    private fun akilliBinaTurunuBaslat(tvDurum: TextView, tvBinaKuyrugu: TextView) {
        val oyun = kayitliNokta("oyun")
        val anaBina = kayitliNokta("ana_bina")
        val gorev = binaKuyrugu.firstOrNull() ?: return

        if (anaBina == null) {
            tvDurum.text = "Hata: Önce Köydeki Ana Binayı Seç!"
            return
        }

        turDevamEdiyor = true
        val girisAdimlari = mutableListOf<Adim>()
        if (oyun != null) girisAdimlari.add(Adim.Dokun(oyun.first, oyun.second, 1800L, "Köy ekranına dönülüyor"))
        girisAdimlari.add(Adim.Dokun(anaBina.first, anaBina.second, 2400L, "Ana Bina açılıyor..."))

        adimlariYurut(girisAdimlari, 0, tvDurum) {
            ekrandaBinayiAraVeBas(gorev, 0, tvDurum, tvBinaKuyrugu)
        }
    }

    private fun ekrandaBinayiAraVeBas(
        gorev: BinaGorevi,
        kaydirmaSayisi: Int,
        tvDurum: TextView,
        tvBinaKuyrugu: TextView
    ) {
        if (!aktifMi) {
            turDevamEdiyor = false
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            tvDurum.text = "Ekran okunamadı, köye dönülüyor..."
            turBitirVeKoyeDon()
            return
        }

        val tumDugumler = mutableListOf<AccessibilityNodeInfo>()
        dugumleriTopla(root, tumDugumler)

        val ekranYuksekligi = resources.displayMetrics.heightPixels

        // 1. Ekranda aranan binanın adını taşıyan metni bul
        val binaDugumu = tumDugumler.firstOrNull { node ->
            val yazi = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            yazi.equals(gorev.ad, ignoreCase = true) &&
                rect.centerY() > (ekranYuksekligi * 0.14) &&
                rect.centerY() < (ekranYuksekligi * 0.84)
        }

        if (binaDugumu != null) {
            val binaRect = Rect()
            binaDugumu.getBoundsInScreen(binaRect)
            val binaY = binaRect.centerY()

            // 2. Aynı satırdaki (dikeyde +-130px yakınlıktaki) yükseltme butonunu bul
            val satirdakiButonlar = tumDugumler.filter { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val yazi = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
                abs(rect.centerY() - binaY) < 130 &&
                    rect.centerX() > binaRect.centerX() &&
                    (yazi.contains("seviye") || yazi.contains("yükselt") || yazi.contains("inşa"))
            }

            // Satırdaki mevcut seviye numarasını kontrol et
            val seviyeNumaralari = satirdakiButonlar.mapNotNull { node ->
                val yazi = (node.text?.toString() ?: node.contentDescription?.toString() ?: "")
                Regex("(\\d+)").find(yazi)?.groupValues?.get(1)?.toIntOrNull()
            }

            // Eğer buton üzerindeki seviye hedef seviyeden büyükse (örn: hedef 6 ama ekranda "7. Seviyeye yükselt" yazıyorsa) görev tamamlanmıştır!
            if (seviyeNumaralari.any { it > gorev.hedefSeviye }) {
                tvDurum.text = "✅ ${gorev.ad} hedef Sv.${gorev.hedefSeviye} tamamlandı!"
                binaKuyrugu.remove(gorev)
                kuyruguGuncelleVeKaydet(tvBinaKuyrugu)
                turBitirVeKoyeDon()
                return
            }

            // Basılacak yükseltme butonunu seç
            val hedefButon = satirdakiButonlar.firstOrNull { node ->
                val yazi = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
                yazi.contains("yükselt") || yazi.contains("seviye")
            }

            if (hedefButon != null) {
                val btnRect = Rect()
                hedefButon.getBoundsInScreen(btnRect)
                val btnYazi = hedefButon.text?.toString() ?: "Yükselt"
                tvDurum.text = "🔨 ${gorev.ad}: '$btnYazi' basılıyor!"
                dokun(btnRect.centerX().toFloat(), btnRect.centerY().toFloat())

                handler.postDelayed({
                    turBitirVeKoyeDon()
                }, 1600L)
                return
            }
        }

        // Bina ekranda henüz görünmüyorsa aşağı kaydır ve tekrar tara (maksimum 7 kaydırma)
        if (kaydirmaSayisi < 7) {
            tvDurum.text = "🔍 '${gorev.ad}' aranıyor... (${kaydirmaSayisi + 1}/7)"
            asagiKaydir()
            handler.postDelayed({
                ekrandaBinayiAraVeBas(gorev, kaydirmaSayisi + 1, tvDurum, tvBinaKuyrugu)
            }, 1300L)
        } else {
            tvDurum.text = "⚠️ '${gorev.ad}' bulunamadı, sonraki tura bırakıldı"
            turBitirVeKoyeDon()
        }
    }

    private fun dugumleriTopla(node: AccessibilityNodeInfo?, liste: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        liste.add(node)
        for (i in 0 until node.childCount) {
            dugumleriTopla(node.getChild(i), liste)
        }
    }

    private fun turBitirVeKoyeDon() {
        val oyun = kayitliNokta("oyun")
        if (oyun != null) {
            dokun(oyun.first, oyun.second)
        }
        handler.postDelayed({
            turDevamEdiyor = false
        }, 1500L)
    }

    // --- DİĞER SEKMELER ---
    private fun askerTurunuCalistir(tvDurum: TextView) {
        val oyun = kayitliNokta("oyun")
        val kisla = kayitliNokta("kisla")
        val egit1 = kayitliNokta("egit1")
        val egit2 = kayitliNokta("egit2")

        if (kisla == null || egit1 == null) {
            tvDurum.text = "Hata: Kışla ve Eğit noktalarını seç!"
            return
        }

        val adimlar = mutableListOf<Adim>()
        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1800L, "Köy ekranına dönülüyor"))
        adimlar.add(Adim.Dokun(kisla.first, kisla.second, 2200L, "Kışla açılıyor"))
        adimlar.add(Adim.Dokun(egit1.first, egit1.second, 1200L, "1. Birim eğitiliyor"))
        if (egit2 != null) adimlar.add(Adim.Dokun(egit2.first, egit2.second, 1200L, "2. Birim eğitiliyor"))
        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1500L, "Köy ekranına dönüldü"))

        turDevamEdiyor = true
        adimlariYurut(adimlar, 0, tvDurum) { turDevamEdiyor = false }
    }

    private fun yagmaTurunuCalistir(tvDurum: TextView) {
        val oyun = kayitliNokta("oyun")
        val yagmaAc = kayitliNokta("yagma_ac")
        val yagmaA = kayitliNokta("yagma_a")

        if (yagmaAc == null || yagmaA == null) {
            tvDurum.text = "Hata: Yağma ikonunu ve A butonunu seç!"
            return
        }

        val koySayisi = (overlayView.findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 15).coerceIn(1, 100)
        val kaydirAdim = (overlayView.findViewById<EditText>(R.id.etYagmaKaydirAdim).text.toString().toIntOrNull() ?: 5).coerceAtLeast(1)

        val adimlar = mutableListOf<Adim>()
        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1800L, "Köy ekranına dönülüyor"))
        adimlar.add(Adim.Dokun(yagmaAc.first, yagmaAc.second, 2400L, "Yağma Asistanı açılıyor"))

        for (i in 1..koySayisi) {
            adimlar.add(Adim.Dokun(yagmaA.first, yagmaA.second, 850L + (0..300).random(), "Yağma A ($i/$koySayisi)"))
            if (i % kaydirAdim == 0 && i < koySayisi) {
                adimlar.add(Adim.AsagiKaydir(1100L, "Liste aşağı kaydırılıyor"))
            }
        }

        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1500L, "Köy ekranına dönüldü"))

        turDevamEdiyor = true
        adimlariYurut(adimlar, 0, tvDurum) { turDevamEdiyor = false }
    }

    private fun temizlikTurunuCalistir(tvDurum: TextView) {
        val oyun = kayitliNokta("oyun")
        val ictima = kayitliNokta("ictima")
        val sekme = kayitliNokta("temizlik_sekme")
        val baslat = kayitliNokta("temizlik_baslat")

        if (ictima == null || sekme == null || baslat == null) {
            tvDurum.text = "Hata: Temizlik noktalarını seç!"
            return
        }

        val adimlar = mutableListOf<Adim>()
        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1800L, "Köy ekranına dönülüyor"))
        adimlar.add(Adim.Dokun(ictima.first, ictima.second, 2200L, "İçtima Meydanı açılıyor"))
        adimlar.add(Adim.Dokun(sekme.first, sekme.second, 1800L, "Temizlik sekmesi açılıyor"))
        adimlar.add(Adim.Dokun(baslat.first, baslat.second, 1500L, "Temizlik başlatıldı"))
        if (oyun != null) adimlar.add(Adim.Dokun(oyun.first, oyun.second, 1500L, "Köy ekranına dönüldü"))

        turDevamEdiyor = true
        adimlariYurut(adimlar, 0, tvDurum) { turDevamEdiyor = false }
    }

    private fun adimlariYurut(adimlar: List<Adim>, index: Int, tvDurum: TextView, onBitti: () -> Unit) {
        if (!aktifMi || index >= adimlar.size) {
            onBitti()
            return
        }

        val suAnki = adimlar[index]
        val beklemeSuresi = when (suAnki) {
            is Adim.Dokun -> {
                tvDurum.text = suAnki.aciklama
                dokun(suAnki.x, suAnki.y)
                suAnki.bekleMs
            }
            is Adim.AsagiKaydir -> {
                tvDurum.text = suAnki.aciklama
                asagiKaydir()
                suAnki.bekleMs
            }
        }

        handler.postDelayed({
            adimlariYurut(adimlar, index + 1, tvDurum, onBitti)
        }, beklemeSuresi)
    }

    private fun noktaSec(anahtar: String, mesaj: String, tvDurum: TextView) {
        if (targetPickerView != null) return
        klavyeOdagiAc(false)

        val picker = TextView(this).apply {
            text = "🎯 $mesaj"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(125, 180, 40, 20))
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

    private fun dokun(x: Float, y: Float) {
        val sapmaX = x + (-4..4).random()
        val sapmaY = y + (-4..4).random()
        val path = Path().apply { moveTo(sapmaX, sapmaY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun asagiKaydir() {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        val startX = (w * 0.5f) + (-15..15).random()
        val startY = h * 0.72f
        val endY = h * 0.36f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        aktifMi = false
        turDevamEdiyor = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}
