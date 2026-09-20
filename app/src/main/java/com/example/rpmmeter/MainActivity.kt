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
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var statusLine1: TextView // Состояние двигателя / HOLD
    private lateinit var statusLine2: TextView // Громкость и порог
    private lateinit var statusLine3: TextView // Pre-Freq, мотор, реальная частота
    
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

        // --- ТРИ СТРОКИ СТАТУСА ---
        
        // Строка 1: Состояние двигателя / HOLD
        statusLine1 = TextView(this).apply {
            text = "Ожидание запуска двигателя (тихо)"
            textSize = 12f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 1)
        }
        rootLayout.addView(statusLine1)

        // Строка 2: Громкость и порог
        statusLine2 = TextView(this).apply {
            text = "Громк: 0 | Пор: 20"
            textSize = 12f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 1, 0, 1)
        }
        rootLayout.addView(statusLine2)

        // Строка 3: Pre-Freq, мотор, реальная частота
        statusLine3 = TextView(this).apply {
            text = "Pre-Freq: 0 Гц | 2T: 0 Гц"
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 1, 0, 6)
        }
        rootLayout.addView(statusLine3)

        // -------------------------

        rootLayout.addView(buildSettingsTable())

        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.1"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }
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
                    
                    // Обновляем информацию по трем строкам
                    if (isHoldActive) {
                        statusLine1.text = "Удержание (HOLD) | Живые: $currentRealRpm об/мин ($modeLabel x$currentMultiplier)"
                        statusLine1.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        statusLine1.text = "Работа мотора"
                        statusLine1.setTextColor(Color.YELLOW)
                    }

                    statusLine2.text = "Громк: $vol | Пор: $currentThreshold"
                    statusLine3.text = "Pre-Freq: ${rawFreq.roundToInt()} Гц | $modeLabel (x$currentMultiplier): ${filteredFreq.roundToInt()} Гц"

                    updateRpmDisplay(displayVal)
                    updateVolumeSquaresUI(vol)
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    statusLine1.text = errorMsg
                    statusLine1.setTextColor(Color.RED)
                }
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Левая колонка: EXIT сверху, x1 и x2 снизу
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.24f)
        }

        btnExit = Button(this).apply {
            text = "EXIT"
            textSize = 12f
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                100
            ).apply { setMargins(0, 0, 0, 4) }
        }
        leftCol.addView(btnExit)

        val subLeft = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pSub = LinearLayout.LayoutParams(0, 44, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        btnX1 = Button(this).apply {
            text = "x1"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 1; refreshAllUI() }
            layoutParams = pSub
        }
        subLeft.addView(btnX1)

        btnX2 = Button(this).apply {
            text = "x2"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 2; refreshAllUI() }
            layoutParams = pSub
        }
        subLeft.addView(btnX2)

        leftCol.addView(subLeft)
        container.addView(leftCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Центральный блок (RPM)
        val rpmBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f)
        }

        rpmTextView = TextView(this).apply {
            text = "00000"
            textSize = 62f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        rpmBlock.addView(rpmTextView)

        val rpmLabel = TextView(this).apply {
            text = "RPM (об / мин)"
            textSize = 11f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        rpmBlock.addView(rpmLabel)

        container.addView(rpmBlock)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Правая колонка: HOLD сверху, x3 и x4 снизу
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.24f)
        }

        btnHold = Button(this).apply {
            text = "HOLD"
            textSize = 12f
            setOnClickListener {
                isHoldActive = !isHoldActive
                if (isHoldActive) heldRpmValue = currentRealRpm
                refreshAllUI()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                100
            ).apply { setMargins(0, 0, 0, 4) }
        }
        rightCol.addView(btnHold)

        val subRight = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnX3 = Button(this).apply {
            text = "x3"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 3; refreshAllUI() }
            layoutParams = pSub
        }
        subRight.addView(btnX3)

        btnX4 = Button(this).apply {
            text = "x4"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 4; refreshAllUI() }
            layoutParams = pSub
        }
        subRight.addView(btnX4)

        rightCol.addView(subRight)
        container.addView(rightCol)

        return container
    }

    private fun buildSettingsTable(): View {
        val table = TableLayout(this).apply {
            setPadding(0, 2, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btn2T = Button(this).apply { text = "2T"; setOnClickListener { prefsManager.engineType = 2; refreshAllUI() } }
        btn4T = Button(this).apply { text = "4T"; setOnClickListener { prefsManager.engineType = 4; refreshAllUI() } }
        btnOthers = Button(this).apply { text = "Озеро"; setOnClickListener { prefsManager.engineType = 3; refreshAllUI() } }

        btnLimit1 = Button(this).apply { text = "6k"; setOnClickListener { prefsManager.maxAllowedRpm = 6000; refreshAllUI() } }
        btnLimit2 = Button(this).apply { text = "12k"; setOnClickListener { prefsManager.maxAllowedRpm = 12000; refreshAllUI() } }
        btnLimit3 = Button(this).apply { text = "20k"; setOnClickListener { prefsManager.maxAllowedRpm = 20000; refreshAllUI() } }

        btnRateFast = Button(this).apply { text = "Fast"; setOnClickListener { prefsManager.audioBufferSize = 1536; refreshAllUI() } }
        btnRateNorm = Button(this).apply { text = "Norm"; setOnClickListener { prefsManager.audioBufferSize = 2560; refreshAllUI() } }
        btnRateSlow = Button(this).apply { text = "Slow"; setOnClickListener { prefsManager.audioBufferSize = 4096; refreshAllUI() } }

        btnSmoothSharp = Button(this).apply { text = "Sharp"; setOnClickListener { prefsManager.saveSmooth(0.02f, 0.05f); refreshAllUI() } }
        btnSmoothNorm = Button(this).apply { text = "Norm"; setOnClickListener { prefsManager.saveSmooth(0.06f, 0.18f); refreshAllUI() } }
        btnSmoothSoft = Button(this).apply { text = "Soft"; setOnClickListener { prefsManager.saveSmooth(0.15f, 0.40f); refreshAllUI() } }

        addRow(table, "мотор:", btn2T, btn4T, btnOthers)
        addRow(table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)
        addVolumeSquaresRow(table, "VU-метр:")

        return table
    }

    private fun addRow(table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
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

        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }
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
        val row = TableRow(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
        }

        val label = TextView(this).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }

        val squaresLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        for (i in 0 until 10) {
            val squareBtn = Button(this).apply {
                text = ""
                textSize = 10f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minimumWidth = 0
                val thresholdValue = getThresholdForSquare(i)
                setOnClickListener {
                    prefsManager.minVolumeThreshold = thresholdValue
                    refreshAllUI()
                }
            }

            val p = LinearLayout.LayoutParams(0, 42, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
            squareBtn.layoutParams = p

            volumeStepButtons[i] = squareBtn
            squaresLayout.addView(squareBtn)
        }

        squaresLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)

        row.addView(label)
        row.addView(squaresLayout)
        table.addView(row)
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
        btnLimit1.setBackgrou
