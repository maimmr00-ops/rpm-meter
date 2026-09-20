package com.example.rpmmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var rpmText: TextView
    private lateinit var debugText: TextView
    private lateinit var btnHold: Button
    
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button
    private lateinit var btnElectro: Button
    
    private lateinit var btnLimit1: Button
    private lateinit var btnLimit2: Button
    private lateinit var btnLimit3: Button
    
    private lateinit var btnRateFast: Button
    private lateinit var btnRateNorm: Button
    private lateinit var btnRateSlow: Button

    private lateinit var btnSmoothSharp: Button
    private lateinit var btnSmoothNorm: Button
    private lateinit var btnSmoothSoft: Button

    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentRealRpm = 0

    private lateinit var prefsManager: PreferencesManager
    private lateinit var audioAnalyzer: AudioAnalyzer
    private val PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        prefsManager = PreferencesManager(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        rootLayout.addView(buildTopPanel())

        statusText = TextView(this).apply {
            text = "Ожидание запуска мотора..."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        rootLayout.addView(statusText)

        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 12)
        }
        rootLayout.addView(debugText)

        rootLayout.addView(buildSettingsTable())

        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 0.2"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 12)
        }
        rootLayout.addView(copyright)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAllUI()

        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            onUpdate = { rpm, freq, vol, status ->
                currentRealRpm = rpm
                runOnUiThread {
                    debugText.text = "Громкость: $vol | Частота: ${freq.toInt()} Гц"
                    if (isHoldActive) {
                        rpmText.text = (if (heldRpmValue > 0) heldRpmValue else 0).toString()
                        statusText.text = "Удержание (HOLD)"
                    } else {
                        rpmText.text = rpm.toString()
                        statusText.text = status
                    }
                }
            },
            onError = { errorMsg ->
                runOnUiThread { statusText.text = errorMsg }
            }
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        } else {
            audioAnalyzer.start()
        }
    }

    private fun buildTopPanel(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 0.2f) })

        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }

        rpmText = TextView(this).apply {
            text = "0"
            textSize = 64f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
        }
        centerCol.addView(rpmText)

        val rpmLabel = TextView(this).apply {
            text = "RPM"
            textSize = 15f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 2)
        }
        centerCol.addView(rpmLabel)
        container.addView(centerCol)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, 110, 0.2f)
        }

        btnHold = Button(this).apply {
            text = "HOLD"
            textSize = 11f
            setOnClickListener {
                isHoldActive = !isHoldActive
                if (isHoldActive) heldRpmValue = currentRealRpm
                refreshAllUI()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(4, 0, 0, 0)
            }
        }
        rightCol.addView(btnHold)
        container.addView(rightCol)

        return container
    }

    private fun buildSettingsTable(): View {
        val table = TableLayout(this).apply {
            setPadding(0, 4, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btn2T = Button(this).apply { text = "2T"; setOnClickListener { prefsManager.engineType = 2; refreshAllUI() } }
        btn4T = Button(this).apply { text = "4T"; setOnClickListener { prefsManager.engineType = 4; refreshAllUI() } }
        btnElectro = Button(this).apply { text = "Электро"; setOnClickListener { prefsManager.engineType = 3; refreshAllUI() } }

        btnLimit1 = Button(this).apply { text = "6k"; setOnClickListener { prefsManager.maxAllowedRpm = 6000; refreshAllUI() } }
        btnLimit2 = Button(this).apply { text = "12k"; setOnClickListener { prefsManager.maxAllowedRpm = 12000; refreshAllUI() } }
        btnLimit3 = Button(this).apply { text = "20k"; setOnClickListener { prefsManager.maxAllowedRpm = 20000; refreshAllUI() } }

        btnRateFast = Button(this).apply { text = "Fast"; setOnClickListener { prefsManager.audioBufferSize = 1536; refreshAllUI() } }
        btnRateNorm = Button(this).apply { text = "Norm"; setOnClickListener { prefsManager.audioBufferSize = 2560; refreshAllUI() } }
        btnRateSlow = Button(this).apply { text = "Slow"; setOnClickListener { prefsManager.audioBufferSize = 4096; refreshAllUI() } }

        btnSmoothSharp = Button(this).apply { text = "Sharp"; setOnClickListener { prefsManager.saveSmooth(0.02f, 0.05f); refreshAllUI() } }
        btnSmoothNorm = Button(this).apply { text = "Norm"; setOnClickListener { prefsManager.saveSmooth(0.06f, 0.18f); refreshAllUI() } }
        btnSmoothSoft = Button(this).apply { text = "Soft"; setOnClickListener { prefsManager.saveSmooth(0.15f, 0.40f); refreshAllUI() } }

        addRow(table, "мотор:", btn2T, btn4T, btnElectro)
        addRow(table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        return table
    }

    private fun addRow(table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }

        val label = TextView(this).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }

        val bLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 0, 2, 0) }
        b1.layoutParams = p
        b2.layoutParams = p
        b3.layoutParams = p

        bLayout.addView(b1)
        bLayout.addView(b2)
        bLayout.addView(b3)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)

        row.addView(label)
        row.addView(bLayout)
        table.addView(row)
    }

    private fun refreshAllUI() {
        if (!::btnHold.isInitialized) return

        btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)

        val eType = prefsManager.engineType
        btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        btnElectro.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnElectro.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

        val limit = prefsManager.maxAllowedRpm
        btnLimit1.setBackgroundColor(if (limit == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit2.setBackgroundColor(if (limit == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit3.setBackgroundColor(if (limit == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        listOf(btnLimit1, btnLimit2, btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        val bufSize = prefsManager.audioBufferSize
        btnRateFast.setBackgroundColor(if (bufSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateNorm.setBackgroundColor(if (bufSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateSlow.setBackgroundColor(if (bufSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        listOf(btnRateFast, btnRateNorm, btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        val rise = prefsManager.riseTimeConstant
        val isSharp = (rise == 0.02f)
        val isNorm = (rise == 0.06f)
        btnSmoothSharp.setBackgroundColor(if (isSharp) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothNorm.setBackgroundColor(if (isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSoft.setBackgroundColor(if (!isSharp && !isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        listOf(btnSmoothSharp, btnSmoothNorm, btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            audioAnalyzer.start()
        }
    }

    override fun onDestroy() {
        audioAnalyzer.stop()
        super.onDestroy()
    }
}
