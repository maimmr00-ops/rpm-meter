package com.example.rpmmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var btnHold: Button
    private lateinit var btnExit: Button
    
    // Кнопки коэффициентов цилиндров (x1, x2, x3, x4)
    private lateinit var btnX1: Button
    private lateinit var btnX2: Button
    private lateinit var btnX3: Button
    private lateinit var btnX4: Button
    private var currentMultiplier = 1

    private lateinit var btn2T: Button
    private lateinit var btn4T: Button
    private lateinit var btnOthers: Button
    
    private lateinit var btnLimit1: Button
    private lateinit var btnLimit2: Button
    private lateinit var btnLimit3: Button
    
    private lateinit var btnRateFast: Button
    private lateinit var btnRateNorm: Button
    private lateinit var btnRateSlow: Button

    private lateinit var btnSmoothSharp: Button
    private lateinit var btnSmoothNorm: Button
    private lateinit var btnSmoothSoft: Button

    private val volumeStepButtons = arrayOfNulls<Button>(10)

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
        statusText.text = "Ожидание запуска двигателя (тихо)"
        statusText.textSize = 12f
        statusText.setTextColor(Color.YELLOW)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 4, 0, 8)
        rootLayout.addView(statusText)

        rootLayout.addView(buildSettingsTable())

        val copyright = TextView(this)
        copyright.text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.0"
        copyright.textSize = 12f
        copyright.setTextColor(Color.parseColor("#9E9E9E"))
        copyright.gravity = Gravity.CENTER
        copyright.setPadding(16, 16, 16, 8)
        rootLayout.addView(copyright)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAllUI()

        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            onUpdate = { rpm, rawFreq, filteredFreq, vol, status ->
                currentRealRpm = (rpm * currentMultiplier)
                
                runOnUiThread {
                    val modeLabel = when (prefsManager.engineType) {
                        2 -> "2T"
                        4 -> "4T"
                        else -> "Озеро"
                    }
                    val currentThreshold = prefsManager.minVolumeThreshold
                    
                    val displayVal = if (isHoldActive) {
                        if (heldRpmValue > 0) heldRpmValue else 0
                    } else {
                        currentRealRpm
                    }
                    
                    if (isHoldActive) {
                        statusText.text = "Удержание (HOLD) | Громкость: $vol (Порог: $currentThreshold) | Pre-Freq: ${rawFreq.roundToInt()} Гц | Живые: $currentRealRpm об/мин ($modeLabel x$currentMultiplier)"
                    } else {
                        statusText.text = "Громкость: $vol (Порог: $currentThreshold) | Pre-Freq: ${rawFreq.roundToInt()} Гц | $modeLabel (x$currentMultiplier): ${filteredFreq.roundToInt()} Гц"
                    }

                    updateRpmDisplay(displayVal)
                    updateVolumeSquaresUI(vol)
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

    private fun updateRpmDisplay(value: Int) {
        val clamped = value.coerceIn(0, 99999)
        val formatted = String.format("%5d", clamped).replace(' ', '\u00A0')
        rpmTextView.text = formatted
    }

    private fun buildTopPanel(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setPadding(0, 4, 0, 4)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val leftCol = LinearLayout(this)
        leftCol.orientation = LinearLayout.VERTICAL
        leftCol.gravity = Gravity.CENTER
        leftCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)

        btnExit = Button(this)
        btnExit.text = "EXIT"
        btnExit.textSize = 11f
        btnExit.setOnClickListener { finish() }
        val pBtn = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        pBtn.setMargins(0, 1, 0, 1)
        btnExit.layoutParams = pBtn
        leftCol.addView(btnExit)

        val subLeft = LinearLayout(this)
        subLeft.orientation = LinearLayout.HORIZONTAL
        subLeft.layoutParams = pBtn

        btnX1 = Button(this)
        btnX1.text = "x1"
        btnX1.textSize = 10f
        btnX1.setPadding(0,0,0,0)
        btnX1.setOnClickListener { currentMultiplier = 1; refreshAllUI() }
        val pSub = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        pSub.setMargins(1, 0, 1, 0)
        btnX1.layoutParams = pSub
        subLeft.addView(btnX1)

        btnX2 = Button(this)
        btnX2.text = "x2"
        btnX2.textSize = 10f
        btnX2.setPadding(0,0,0,0)
        btnX2.setOnClickListener { currentMultiplier = 2; refreshAllUI() }
        btnX2.layoutParams = pSub
        subLeft.addView(btnX2)

        leftCol.addView(subLeft)
        container.addView(leftCol)

        val leftSpacer = View(this)
        leftSpacer.layoutParams = LinearLayout.LayoutParams(6, 1)
        container.addView(leftSpacer)

        val rpmBlock = LinearLayout(this)
        rpmBlock.orientation = LinearLayout.VERTICAL
        rpmBlock.gravity = Gravity.CENTER
        rpmBlock.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)

        rpmTextView = TextView(this)
        rpmTextView.text = "00000"
        rpmTextView.textSize = 68f
        rpmTextView.setTextColor(Color.parseColor("#00E676"))
        rpmTextView.gravity = Gravity.CENTER
        rpmTextView.includeFontPadding = false
        rpmBlock.addView(rpmTextView)

        val rpmLabel = TextView(this)
        rpmLabel.text = "RPM (об / мин)"
        rpmLabel.textSize = 11f
        rpmLabel.setTextColor(Color.parseColor("#80CBC4"))
        rpmLabel.gravity = Gravity.CENTER
        rpmLabel.includeFontPadding = false
        rpmBlock.addView(rpmLabel)

        container.addView(rpmBlock)

        val rightSpacer = View(this)
        rightSpacer.layoutParams = LinearLayout.LayoutParams(6, 1)
        container.addView(rightSpacer)

        val rightCol = LinearLayout(this)
        rightCol.orientation = LinearLayout.VERTICAL
        rightCol.gravity = Gravity.CENTER
        rightCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)

        btnHold = Button(this)
        btnHold.text = "HOLD"
        btnHold.textSize = 11f
        btnHold.setOnClickListener {
            isHoldActive = !isHoldActive
            if (isHoldActive) heldRpmValue = currentRealRpm
            refreshAllUI()
        }
        btnHold.layoutParams = pBtn
        rightCol.addView(btnHold)

        val subRight = LinearLayout(this)
        subRight.orientation = LinearLayout.HORIZONTAL
        subRight.layoutParams = pBtn

        btnX3 = Button(this)
        btnX3.text = "x3"
        btnX3.textSize = 10f
        btnX3.setPadding(0,0,0,0)
        btnX3.setOnClickListener { currentMultiplier = 3; refreshAllUI() }
        btnX3.layoutParams = pSub
        subRight.addView(btnX3)

        btnX4 = Button(this)
        btnX4.text = "x4"
        btnX4.textSize = 10f
        btnX4.setPadding(0,0,0,0)
        btnX4.setOnClickListener { currentMultiplier = 4; refreshAllUI() }
        btnX4.layoutParams = pSub
        subRight.addView(btnX4)

        rightCol.addView(subRight)
        container.addView(rightCol)

        return container
    }

    private fun buildSettingsTable(): View {
        val table = TableLayout(this)
        table.setPadding(0, 2, 0, 0)
        table.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        btn2T = Button(this)
        btn2T.text = "2T"
        btn2T.setOnClickListener { prefsManager.engineType = 2; refreshAllUI() }

        btn4T = Button(this)
        btn4T.text = "4T"
        btn4T.setOnClickListener { prefsManager.engineType = 4; refreshAllUI() }

        btnOthers = Button(this)
        btnOthers.text = "Озеро"
        btnOthers.setOnClickListener { prefsManager.engineType = 3; refreshAllUI() }

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

        addRow(table, "мотор:", btn2T, btn4T, btnOthers)
        addRow(table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)
        addVolumeSquaresRow(table, "VU-метр:")

        return table
    }

    private fun addRow(table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, 2, 0, 2)

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

    private fun getThresholdForSquare(index: Int): Int {
        return when (index) {
            0 -> 20
            1 -> 150
            2 -> 400
            3 -> 800
            4 -> 1400
            5 -> 2200
            6 -> 3200
            7 -> 4800
            8 -> 6800
            else -> 9000
        }
    }

    private fun addVolumeSquaresRow(table: TableLayout, labelText: String) {
        val row = TableRow(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, 2, 0, 2)

        val label = TextView(this)
        label.text = labelText
        label.textSize = 12f
        label.setTextColor(Color.parseColor("#B0BEC5"))
        label.setPadding(0, 0, 8, 0)

        val squaresLayout = LinearLayout(this)
        squaresLayout.orientation = LinearLayout.HORIZONTAL
        squaresLayout.gravity = Gravity.CENTER

        for (i in 0 until 10) {
            val squareBtn = Button(this)
            val thresholdValue = getThresholdForSquare(i)
            
            squareBtn.text = ""
            squareBtn.textSize = 10f
            squareBtn.setPadding(0, 0, 0, 0)
            squareBtn.minWidth = 0
            squareBtn.minimumWidth = 0

            squareBtn.setOnClickListener {
                prefsManager.minVolumeThreshold = thresholdValue
                refreshAllUI()
            }

            val p = LinearLayout.LayoutParams(0, 42, 1f)
            p.setMargins(1, 0, 1, 0)
            squareBtn.layoutParams = p

            volumeStepButtons[i] = squareBtn
            squaresLayout.addView(squareBtn)
        }

        val rowParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
        squaresLayout.layoutParams = rowParams

        row.addView(label)
        row.addView(squaresLayout)
        table.addView(row)
    }

    private fun refreshAllUI() {
        if (!::btnHold.isInitialized) return

        btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)

        btnExit.setBackgroundColor(Color.parseColor("#424242"))
        btnExit.setTextColor(Color.WHITE)

        btnX1.setBackgroundColor(if (currentMultiplier == 1) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX1.setTextColor(if (currentMultiplier == 1) Color.BLACK else Color.WHITE)
        btnX2.setBackgroundColor(if (currentMultiplier == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX2.setTextColor(if (currentMultiplier == 2) Color.BLACK else Color.WHITE)
        btnX3.setBackgroundColor(if (currentMultiplier == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX3.setTextColor(if (currentMultiplier == 3) Color.BLACK else Color.WHITE)
        btnX4.setBackgroundColor(if (currentMultiplier == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX4.setTextColor(if (currentMultiplier == 4) Color.BLACK else Color.WHITE)

        val eType = prefsManager.engineType
        btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        btnOthers.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnOthers.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

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

        updateVolumeSquaresUI(0)
    }

    private fun updateVolumeSquaresUI(currentVol: Int) {
        val currentSensitivityThreshold = prefsManager.minVolumeThreshold
        
        var thresholdIndex = 0
        if (prefsManager.hasStoredThreshold()) {
            for (i in 0 until 10) {
                if (getThresholdForSquare(i) == currentSensitivityThreshold) {
                    thresholdIndex = i
                    break
                }
            }
        }
        
        var volumeIndex = -1
        if (currentVol > 0) {
            for (i in 9 downTo 0) {
                if (currentVol >= getThresholdForSquare(i)) {
                    volumeIndex = i
                    break
                }
            }
        }

        for (i in 0 until 10) {
            val btn = volumeStepButtons[i] ?: continue
            
            when {
                i == thresholdIndex && volumeIndex >= i -> {
                    btn.setBackgroundColor(Color.parseColor("#00E676"))
                }
                i == thresholdIndex -> {
                    btn.setBackgroundColor(Color.parseColor("#FF9800"))
                }
                i < thresholdIndex && volumeIndex >= i -> {
                    btn.setBackgroundColor(Color.parseColor("#00BCD4"))
                }
                i > thresholdIndex && volumeIndex >= i -> {
                    btn.setBackgroundColor(Color.parseColor("#D0F8E8"))
                }
                else -> {
                    btn.setBackgroundColor(Color.parseColor("#37474F"))
                }
            }
        }
    
