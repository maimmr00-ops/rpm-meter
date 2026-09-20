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

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.parseColor("#121212"))
        scrollView.isFillViewport = true

        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.setPadding(16, 16, 16, 16)
        rootLayout.gravity = Gravity.CENTER_HORIZONTAL

        rootLayout.addView(buildTopPanel())

        statusText = TextView(this)
        statusText.text = "Ожидание запуска мотора..."
        statusText.textSize = 13f
        statusText.setTextColor(Color.LTGRAY)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 8, 0, 0)
        rootLayout.addView(statusText)

        debugText = TextView(this)
        debugText.text = "Громкость: 0 | Частота: 0 Гц"
        debugText.textSize = 11f
        debugText.setTextColor(Color.YELLOW)
        debugText.gravity = Gravity.CENTER
        debugText.setPadding(0, 2, 0, 12)
        rootLayout.addView(debugText)

        rootLayout.addView(buildSettingsTable())

        val copyright = TextView(this)
        copyright.text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 0.3"
        copyright.textSize = 12f
        copyright.setTextColor(Color.parseColor("#9E9E9E"))
        copyright.gravity = Gravity.CENTER
        copyright.setPadding(16, 20, 16, 12)
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
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setPadding(0, 12, 0, 4)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val leftSpacer = View(this)
        leftSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 0.05f)
        container.addView(leftSpacer)

        val rpmContainer = LinearLayout(this)
        rpmContainer.orientation = LinearLayout.HORIZONTAL
        rpmContainer.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        rpmContainer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f)

        rpmText = TextView(this)
        rpmText.text = "0"
        rpmText.textSize = 76f
        rpmText.setTextColor(Color.parseColor("#00E676"))
        rpmText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        rpmText.includeFontPadding = false

        val textWrapper = LinearLayout(this)
        textWrapper.orientation = LinearLayout.HORIZONTAL
        textWrapper.gravity = Gravity.END
        textWrapper.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textWrapper.addView(rpmText)
        rpmContainer.addView(textWrapper)

        val rpmLabel = TextView(this)
        rpmLabel.text = " RPM"
        rpmLabel.textSize = 22f
        rpmLabel.setTextColor(Color.parseColor("#80CBC4"))
        rpmLabel.gravity = Gravity.BOTTOM or Gravity.START
        rpmLabel.setPadding(4, 0, 0, 16)
        rpmLabel.includeFontPadding = false
        rpmContainer.addView(rpmLabel)

        container.addView(rpmContainer)

        val rightCol = LinearLayout(this)
        rightCol.orientation = LinearLayout.VERTICAL
        rightCol.gravity = Gravity.CENTER_HORIZONTAL
        rightCol.layoutParams = LinearLayout.LayoutParams(0, 120, 0.2f)

        btnHold = Button(this)
        btnHold.text = "HOLD"
        btnHold.textSize = 11f
        btnHold.setOnClickListener {
            isHoldActive = !isHoldActive
            if (isHoldActive) heldRpmValue = currentRealRpm
            refreshAllUI()
        }
        
        val holdParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        holdParams.setMargins(8, 0, 0, 0)
        btnHold.layoutParams = holdParams
        rightCol.addView(btnHold)
        
        container.addView(rightCol)

        return container
    }

    private fun buildSettingsTable(): View {
        val table = TableLayout(this)
        table.setPadding(0, 4, 0, 0)
        table.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        btn2T = Button(this)
        btn2T.text = "2T"
        btn2T.setOnClickListener { prefsManager.engineType = 2; refreshAllUI() }

        btn4T = Button(this)
        btn4T.text = "4T"
        btn4T.setOnClickListener { prefsManager.engineType = 4; refreshAllUI() }

        btnElectro = Button(this)
        btnElectro.text = "Электро"
        btnElectro.setOnClickListener { prefsManager.engineType = 3; refreshAllUI() }

        btnLimit1 = Button(this)
        btnLimit1.text = "6k"
        btnLimit1.setOnClickListener { prefsManager.maxAllowedRpm = 6000; refreshAllUI() }

        btnLimit2 = Button(this)
        btnLimit2.text = "12k"
        btnLimit2.setOnClickListener { prefsManager.maxAllowedRpm = 12000; refreshAllUI() }

        btnLimit3 = Button(this)
        btnLimit3.text = "20k"
        btnLimit3.setOnClickListener { prefsManager.maxAllowedRpm = 20000; refreshAllUI() }

        btnRateFast = Button(this)
        btnRateFast.text = "Fast"
        btnRateFast.setOnClickListener { prefsManager.audioBufferSize = 1536; refreshAllUI() }

        btnRateNorm = Button(this)
        btnRateNorm.text = "Norm"
        btnRateNorm.setOnClickListener { prefsManager.audioBufferSize = 2560; refreshAllUI() }

        btnRateSlow = Button(this)
        btnRateSlow.text = "Slow"
        btnRateSlow.setOnClickListener { prefsManager.audioBufferSize = 4096; refreshAllUI() }

        btnSmoothSharp = Button(this)
        btnSmoothSharp.text = "Sharp"
        btnSmoothSharp.setOnClickListener { prefsManager.saveSmooth(0.02f, 0.05f); refreshAllUI() }

        btnSmoothNorm = Button(this)
        btnSmoothNorm.text = "Norm"
        btnSmoothNorm.setOnClickListener { prefsManager.saveSmooth(0.06f, 0.18f); refreshAllUI() }

        btnSmoothSoft = Button(this)
        btnSmoothSoft.text = "Soft"
        btnSmoothSoft.setOnClickListener { prefsManager.saveSmooth(0.15f, 0.40f); refreshAllUI() }

        addRow(table, "мотор:", btn2T, btn4T, btnElectro)
        addRow(table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        return table
    }

    private fun addRow(table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, 3, 0, 3)

        val label = TextView(this)
        label.text = labelText
        label.textSize = 12f
        label.setTextColor(Color.parseColor("#B0BEC5"))
        label.setPadding(0, 0, 8, 0)

        val bLayout = LinearLayout(this)
        bLayout.orientation = LinearLayout.HORIZONTAL
        bLayout.gravity = Gravity.CENTER

        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(2, 0, 2, 0)
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
