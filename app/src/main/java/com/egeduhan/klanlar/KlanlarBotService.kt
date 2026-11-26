package com.egeduhan.klanlar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
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
        "Ana bina", "Kışla", "Ahır", "Atölye", "Gözetleme Kulesi",
        "Akademi", "Demirci", "İçtima Meydanı", "Heykel", "Pazar",
        "Oduncu", "Kil ocağı", "Demir madeni", "Çiftlik", "Depo",
        "Gizli depo", "Sur"
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
        data class MetinYaz(val metin: String, val bekleMs: Long, val aciklama: String) : Adim()
        data class AsagiKaydir(val bekleMs: Long, val aciklama: String) : Adim()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        arayuzuKur()
    }

    private fun insansiSure(tabanMs: Long, ekstraMaxMs: Long = 650L): Long {
        return tabanMs + (150L..ekstraMaxMs).random()
    }

    private fun normalizeTr(metin: String): String {
        return metin.trim()
            .replace('İ', 'i')
            .replace('I', 'ı')
            .lowercase()
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
            R.id.etAskerDakika, R.id.etMizrakSayisi, R.id.etYagmaDakika,
            R.id.etYagmaSayi, R.id.etYagmaKaydirAdim, R.id.etBinaDakika,
            R.id.etBinaHedefSeviye, R.id.etTemizlikDakika
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

        // Merkez: Menü ve Oyun ikonları
        overlayView.findViewById<Button>(R.id.btnNoktaMenu).setOnClickListener {
            noktaSec("menu", "Sol alttaki 'Menü' ikonuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaOyun).setOnClickListener {
            noktaSec("oyun", "Alt menüdeki 'Oyun' simgesine dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnTestKaydir).setOnClickListener {
            val basarili = asagiKaydir()
            tvDurum.text = if (basarili) "Test: Ekran başarıyla aşağı kaydırıldı" else "Test: Kaydırılacak liste bulunamadı"
        }

        // Asker (Kışla)
        overlayView.findViewById<Button>(R.id.btnNoktaMizrakKutu).setOnClickListener {
            noktaSec("mizrak_kutu", "Kışladaki Mızrakçı sayı kutusuna dokun", tvDurum)
        }
        overlayView.findViewById<Button>(R.id.btnNoktaEgit1).setOnClickListener {
            noktaSec("egit1", "Kışladaki 'Eğit' butonuna dokun", tvDurum)
        }

        // Yağma Asistanı
        overlayView.findViewById<Button>(R.id.btnNoktaYagmaA).setOnClickListener {
            noktaSec("yagma_a", "Yağma Asistanındaki 'A' butonuna dokun", tvDurum)
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
                tvDurum.text = "Durum: Akıllı otomasyon başladı..."
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
                        askerAktif && (simdi - sonAskerZamani >= askerSure) -> {
                            sonAskerZamani = simdi
                            askerTurunuCalistir(tvDurum)
                        }
                        yagmaAktif && (simdi - sonYagmaZamani >= yagmaSure) -> {
                            sonYagmaZamani = simdi
                            yagmaTurunuCalistir(tvDurum)
                        }
                        binaAktif && binaKuyrugu.isNotEmpty() && (simdi - sonBinaZamani >= binaSure) -> {
                            sonBinaZamani = simdi
                            akilliBinaTurunuBaslat(tvDurum, tvBinaKuyrugu)
                        }
                        temizlikAktif && (simdi - sonTemizlikZamani >= temizlikSure) -> {
                            sonTemizlikZamani = simdi
                            temizlikTurunuCalistir(tvDurum)
                        }
                        else -> {
                            tvDurum.text = "Durum: Sıradaki tur süresi bekleniyor..."
                        }
                    }
                }

                handler.postDelayed(this, 2500L)
            }
        }
        handler.postDelayed(kontrolGorevi, 1200L)
    }

    // --- DOĞRUDAN ERİŞİLEBİLİRLİK DÜĞÜMÜNE TIKLAMA ---
    private fun dugumeTikla(node: AccessibilityNodeInfo): Boolean {
        var hedef: AccessibilityNodeInfo? = node
        var derinlik = 0
        while (hedef != null && derinlik < 5) {
            if (hedef.isClickable && hedef.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            hedef = hedef.parent
            derinlik++
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            dokun(rect.centerX().toFloat(), rect.centerY().toFloat())
            return true
        }
        return false
    }

    // --- 'MENÜ' İÇİNDEN ÖĞE AÇMA ---
    private fun menudenOgeAc(
        etiket: String,
        ogeAdi: String,
        tvDurum: TextView,
        onIcinde: () -> Unit
    ) {
        val menuNokta = kayitliNokta("menu")
        if (menuNokta == null) {
            tvDurum.text = "$etiket Hata: Merkez'den 'Menü' ikonunu seç!"
            return
        }

        turDevamEdiyor = true
        tvDurum.text = "$etiket Menü açılıyor..."
        dokun(menuNokta.first, menuNokta.second)

        handler.postDelayed({
            metniAraVeTikla(etiket, ogeAdi, 0, tvDurum,
                onBulundu = {
                    handler.postDelayed({ onIcinde() }, insansiSure(2800L, 700L))
                },
                onBulunamadi = {
                    tvDurum.text = "$etiket ⚠️ Menüde '$ogeAdi' bulunamadı!"
                    turBitirVeKoyeDon()
                }
            )
        }, insansiSure(2500L, 600L))
    }

    // --- 'OYUN' (KÖY) EKRANINDAN BİNA AÇMA ---
    private fun oyundanBinayaGir(
        etiket: String,
        binaAdi: String,
        tvDurum: TextView,
        onIcinde: () -> Unit
    ) {
        val oyun = kayitliNokta("oyun")
        turDevamEdiyor = true

        val baslangic = {
            metniAraVeTikla(etiket, binaAdi, 0, tvDurum,
                onBulundu = {
                    handler.postDelayed({ onIcinde() }, insansiSure(2800L, 700L))
                },
                onBulunamadi = {
                    tvDurum.text = "$etiket ⚠️ '$binaAdi' bulunamadı! Klasik Görünüm'ü aç."
                    turBitirVeKoyeDon()
                }
            )
        }

        if (oyun != null) {
            tvDurum.text = "$etiket Köy listesine geçiliyor..."
            dokun(oyun.first, oyun.second)
            handler.postDelayed({ baslangic() }, insansiSure(2600L, 600L))
        } else {
            baslangic()
        }
    }

    private fun metniAraVeTikla(
        etiket: String,
        aranan: String,
        kaydirma: Int,
        tvDurum: TextView,
        onBulundu: () -> Unit,
        onBulunamadi: () -> Unit
    ) {
        if (!aktifMi) {
            turDevamEdiyor = false
            return
        }

        val arananNorm = normalizeTr(aranan)
        val root = rootInActiveWindow
        if (root != null) {
            val dugumler = mutableListOf<AccessibilityNodeInfo>()
            dugumleriTopla(root, dugumler)

            val hedef = dugumler
                .filter { node ->
                    val hamYazi = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    val yaziNorm = normalizeTr(hamYazi)
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    (yaziNorm == arananNorm || yaziNorm.startsWith("$arananNorm (")) &&
                        rect.width() > 2 && rect.height() > 2
                }
                .minByOrNull { node ->
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    rect.width().toLong() * rect.height().toLong()
                }

            if (hedef != null) {
                tvDurum.text = "$etiket 📍 '$aranan' ismine tıklandı!"
                dugumeTikla(hedef)
                onBulundu()
                return
            }
        }

        if (kaydirma < 4) {
            tvDurum.text = "$etiket 🔍 '$aranan' aranıyor (${kaydirma + 1}/4)..."
            asagiKaydir()
            handler.postDelayed({
                metniAraVeTikla(etiket, aranan, kaydirma + 1, tvDurum, onBulundu, onBulunamadi)
            }, insansiSure(1800L, 600L))
        } else {
            onBulunamadi()
        }
    }

    // --- 1. ASKER (KIŞLA) TURU ---
    private fun askerTurunuCalistir(tvDurum: TextView) {
        val mizrakKutu = kayitliNokta("mizrak_kutu")
        val egit1 = kayitliNokta("egit1")

        if (egit1 == null) {
            tvDurum.text = "[Asker] Hata: 'Eğit' butonunu seç!"
            return
        }

        val mizrakAdet = (overlayView.findViewById<EditText>(R.id.etMizrakSayisi).text.toString().toIntOrNull() ?: 3).coerceIn(1, 500).toString()

        oyundanBinayaGir("[Asker]", "Kışla", tvDurum) {
            val adimlar = mutableListOf<Adim>()
            if (mizrakKutu != null) {
                adimlar.add(Adim.Dokun(mizrakKutu.first, mizrakKutu.second, insansiSure(1400L, 500L), "[Asker] Mızrak kutusu seçildi"))
                adimlar.add(Adim.MetinYaz(mizrakAdet, insansiSure(1400L, 500L), "[Asker] $mizrakAdet mızrakçı yazıldı"))
            }
            adimlar.add(Adim.Dokun(egit1.first, egit1.second, insansiSure(2000L, 600L), "[Asker] ⚔️ $mizrakAdet mızrakçı basıldı!"))

            adimlariYurut(adimlar, 0, tvDurum) {
                turBitirVeKoyeDon()
            }
        }
    }

    // --- 2. YAĞMA ASİSTANI TURU ---
    private fun yagmaTurunuCalistir(tvDurum: TextView) {
        val yagmaA = kayitliNokta("yagma_a")
        if (yagmaA == null) {
            tvDurum.text = "[Yağma] Hata: 'A' butonunu seç!"
            return
        }

        val koySayisi = (overlayView.findViewById<EditText>(R.id.etYagmaSayi).text.toString().toIntOrNull() ?: 15).coerceIn(1, 100)
        val kaydirAdim = (overlayView.findViewById<EditText>(R.id.etYagmaKaydirAdim).text.toString().toIntOrNull() ?: 5).coerceAtLeast(1)

        menudenOgeAc("[Yağma]", "Yağma Asistanı", tvDurum) {
            val adimlar = mutableListOf<Adim>()
            for (i in 1..koySayisi) {
                adimlar.add(Adim.Dokun(yagmaA.first, yagmaA.second, insansiSure(1600L, 800L), "[Yağma] 🐎 A ($i/$koySayisi)"))
                if (i % kaydirAdim == 0 && i < koySayisi) {
                    adimlar.add(Adim.AsagiKaydir(insansiSure(1800L, 600L), "[Yağma] Liste kaydırılıyor"))
                }
            }

            adimlariYurut(adimlar, 0, tvDurum) {
                turBitirVeKoyeDon()
            }
        }
    }

    // --- 3. AKILLI BİNA TARAMA VE YÜKSELTME TURU ---
    private fun akilliBinaTurunuBaslat(tvDurum: TextView, tvBinaKuyrugu: TextView) {
        val gorev = binaKuyrugu.firstOrNull() ?: return

        menudenOgeAc("[Bina]", "Ana bina", tvDurum) {
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
            turBitirVeKoyeDon()
            return
        }

        val tumDugumler = mutableListOf<AccessibilityNodeInfo>()
        dugumleriTopla(root, tumDugumler)
        val hedefAdNorm = normalizeTr(gorev.ad)

        val binaDugumu = tumDugumler
            .filter { node ->
                val yaziNorm = normalizeTr(node.text?.toString() ?: node.contentDescription?.toString() ?: "")
                val rect = Rect()
                node.getBoundsInScreen(rect)
                (yaziNorm == hedefAdNorm || yaziNorm.startsWith("$hedefAdNorm (")) &&
                    rect.width() > 2 && rect.height() > 2
            }
            .minByOrNull { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.width().toLong() * rect.height().toLong()
            }

        if (binaDugumu != null) {
            val binaRect = Rect()
            binaDugumu.getBoundsInScreen(binaRect)
            val binaY = binaRect.centerY()

            val satirdakiButonlar = tumDugumler.filter { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val yaziNorm = normalizeTr(node.text?.toString() ?: node.contentDescription?.toString() ?: "")
                abs(rect.centerY() - binaY) < 130 &&
                    rect.centerX() > binaRect.centerX() &&
                    (yaziNorm.contains("seviye") || yaziNorm.contains("yükselt") || yaziNorm.contains("inşa"))
            }

            val seviyeNumaralari = satirdakiButonlar.mapNotNull { node ->
                val yazi = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                Regex("(\\d+)").find(yazi)?.groupValues?.get(1)?.toIntOrNull()
            }

            if (seviyeNumaralari.any { it > gorev.hedefSeviye }) {
                tvDurum.text = "[Bina] ✅ ${gorev.ad} hedef Sv.${gorev.hedefSeviye} tamamlandı!"
                binaKuyrugu.remove(gorev)
                kuyruguGuncelleVeKaydet(tvBinaKuyrugu)
                turBitirVeKoyeDon()
                return
            }

            val hedefButon = satirdakiButonlar.firstOrNull { node ->
                val yaziNorm = normalizeTr(node.text?.toString() ?: node.contentDescription?.toString() ?: "")
                yaziNorm.contains("yükselt") || yaziNorm.contains("seviye")
            }

            if (hedefButon != null) {
                val btnYazi = hedefButon.text?.toString() ?: "Yükselt"
                tvDurum.text = "[Bina] 🔨 ${gorev.ad}: '$btnYazi' tıklandı!"
                dugumeTikla(hedefButon)

                handler.postDelayed({ turBitirVeKoyeDon() }, insansiSure(2200L, 600L))
                return
            }
        }

        if (kaydirmaSayisi < 7) {
            tvDurum.text = "[Bina] 🔍 '${gorev.ad}' aranıyor... (${kaydirmaSayisi + 1}/7)"
            asagiKaydir()
            handler.postDelayed({
                ekrandaBinayiAraVeBas(gorev, kaydirmaSayisi + 1, tvDurum, tvBinaKuyrugu)
            }, insansiSure(1800L, 600L))
        } else {
            tvDurum.text = "[Bina] ⚠️ '${gorev.ad}' bulunamadı, sonraki tura bırakıldı"
            turBitirVeKoyeDon()
        }
    }

    // --- 4. TEMİZLİK TURU ---
    private fun temizlikTurunuCalistir(tvDurum: TextView) {
        val baslat = kayitliNokta("temizlik_baslat")
        if (baslat == null) {
            tvDurum.text = "[Temizlik] Hata: 'Başlat' butonunu seç!"
            return
        }

        menudenOgeAc("[Temizlik]", "İçtima Meydanı", tvDurum) {
            metniAraVeTikla("[Temizlik]", "Temizlik", 0, tvDurum,
                onBulundu = {
                    handler.postDelayed({
                        tvDurum.text = "[Temizlik] 🧹 Temizlik başlatılıyor..."
                        dokun(baslat.first, baslat.second)
                        handler.postDelayed({ turBitirVeKoyeDon() }, insansiSure(2200L, 600L))
                    }, insansiSure(2500L, 600L))
                },
                onBulunamadi = {
                    tvDurum.text = "[Temizlik] ⚠️ 'Temizlik' sekmesi bulunamadı!"
                    turBitirVeKoyeDon()
                }
            )
        }
    }

    // --- ORTAK YARDIMCI FONKSİYONLAR ---
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
            is Adim.MetinYaz -> {
                tvDurum.text = suAnki.aciklama
                odakliKutuyaMetinYaz(suAnki.metin)
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

    private fun odakliKutuyaMetinYaz(metin: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("klanlar_sayi", metin))

        val odakli = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (odakli != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, metin)
            }
            val yazildi = odakli.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!yazildi) {
                odakli.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
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
        if (oyun != null) dokun(oyun.first, oyun.second)
        handler.postDelayed({ turDevamEdiyor = false }, insansiSure(2200L, 600L))
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
        val stroke = GestureDescription.StrokeDescription(path, 0L, (75L..115L).random())
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun asagiKaydir(): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            val dugumler = mutableListOf<AccessibilityNodeInfo>()
            dugumleriTopla(root, dugumler)

            val kaydirilabilir = dugumler
                .filter { it.isScrollable }
                .maxByOrNull {
                    val r = Rect()
                    it.getBoundsInScreen(r)
                    r.width().toLong() * r.height().toLong()
                }

            if (kaydirilabilir != null && kaydirilabilir.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                return true
            }
        }

        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        val startX = (w * 0.25f) + (-10..10).random()
        val startY = h * 0.76f
        val endY = h * 0.28f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, (580L..720L).random())
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        return true
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
