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
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var statusLine1: TextView
    private lateinit var statusLine2: TextView
    private lateinit var statusLine3: TextView
    
    private lateinit var rpmTextView: TextView
    private lateinit var btnHold: Button
    private lateinit var btnExit: Button
    
    private lateinit var btnX1: Button
    private lateinit var btnX2: Button
    private lateinit var btnX3: Button
    private lateinit var btnX4: Button
    private var currentMultiplier = 1

    private lateinit var settings: UIBuilder.SettingsButtons
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

        statusLine1 = TextView(this).apply {
            text = "Ожидание запуска двигателя (тихо)"
            textSize = 12f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 1)
        }
        rootLayout.addView(statusLine1)

        statusLine2 = TextView(this).apply {
            text = "Громк: 0 | Пор: 20"
            textSize = 12f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 1, 0, 1)
        }
        rootLayout.addView(statusLine2)

        statusLine3 = TextView(this).apply {
            text = "Pre-Freq: 0 Гц | 2T: 0 Гц"
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 1, 0, 4)
        }
        rootLayout.addView(statusLine3)

        // Блок кнопок x1-x4 под строкой частот
        rootLayout.addView(buildMultiplierBar())

        // Таблица настроек через UIBuilder
        settings = UIBuilder.buildSettingsTable(
            context = this,
            prefsManager = prefsManager,
            onRefreshUI = { refreshAllUI() },
            volumeStepButtons = volumeStepButtons
        )
        rootLayout.addView(settings.table)

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
                    
                    if (isHoldActive) {
                        statusLine1.text = "HOLD. Текущие: $currentRealRpm об/мин ($modeLabel x$currentMultiplier)"
                        statusLine1.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        if (vol < currentThreshold) {
                            statusLine1.text = "Ожидание запуска двигателя (тихо)"
                            statusLine1.setTextColor(Color.YELLOW)
                        } else {
                            statusLine1.text = "Работа мотора"
                            statusLine1.setTextColor(Color.parseColor("#00E676"))
                        }
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

        // Кнопка EXIT слева (компактная колонка)
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.22f)
        }

        btnExit = Button(this).apply {
            text = "EXIT"
            textSize = 12f
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                100
            )
        }
        leftCol.addView(btnExit)
        container.addView(leftCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Центральный блок с крупными цифрами RPM (шире, чтобы шрифт не мельчал)
        val rpmBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
        }

        rpmTextView = TextView(this).apply {
            text = "00000"
            textSize = 68f // Вернули крупный размер
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

        // Кнопка HOLD справа
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.22f)
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
            )
        }
        rightCol.addView(btnHold)
        container.addView(rightCol)

        return container
    }

    private fun buildMultiplierBar(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnParams = LinearLayout.LayoutParams(0, 48, 1f).apply {
            setMargins(3, 0, 3, 0)
        }

        btnX1 = Button(this).apply {
            text = "x1"; textSize = 12f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 1; refreshAllUI() }
            layoutParams = btnParams
        }
        btnX2 = Button(this).apply {
            text = "x2"; textSize = 12f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 2; refreshAllUI() }
            layoutParams = btnParams
        }
        btnX3 = Button(this).apply {
            text = "x3"; textSize = 12f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 3; refreshAllUI() }
            layoutParams = btnParams
        }
        btnX4 = Button(this).apply {
            text = "x4"; textSize = 12f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 4; refreshAllUI() }
            layoutParams = btnParams
        }

        layout.addView(btnX1)
        layout.addView(btnX2)
        layout.addView(btnX3)
        layout.addView(btnX4)

        return layout
    }

    private fun updateVolumeSquaresUI(currentVol: Int) {
        val currentSensitivityThreshold = prefsManager.minVolumeThreshold
        
        var thresholdIndex = 0
        if (prefsManager.hasStoredThreshold()) {
            for (i in 0 until 10) {
                if (UIBuilder.getThresholdForSquare(i) == currentSensitivityThreshold) {
                    thresholdIndex = i
                    break
                }
            }
        }
        
        var volumeIndex = -1
        if (currentVol > 0) {
            for (i in 9 downTo 0) {
                if (currentVol >= UIBuilder.getThresholdForSquare(i)) {
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
        if (!::btnHold.isInitialized || !::settings.isInitialized) return

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
        settings.btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        settings.btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        settings.btnOthers.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btnOthers.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

        val limit = prefsManager.maxAllowedRpm
        settings.btnLimit1.setBackgroundColor(if (limit == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        settings.btnLimit2.setBackgroundColor(if (limit == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        settings.btnLimit3.setBackgroundColor(if (limit == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        listOf(settings.btnLimit1, settings.btnLimit2, settings.btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        val bufSize = prefsManager.audioBufferSize
        settings.btnRateFast.setBackgroundColor(if (bufSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        settings.btnRateNorm.setBackgroundColor(if (bufSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        settings.btnRateSlow.setBackgroundColor(if (bufSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        listOf(settings.btnRateFast, settings.btnRateNorm, settings.btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        val rise = prefsManager.riseTimeConstant
        val isSharp = (rise == 0.02f)
        val isNorm = (rise == 0.06f)
        settings.btnSmoothSharp.setBackgroundColor(if (isSharp) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        settings.btnSmoothNorm.setBackgroundColor(if (isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        settings.btnSmoothSoft.setBackgroundColor(if (!isSharp && !isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        listOf(settings.btnSmoothSharp, settings.btnSmoothNorm, settings.btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }

        updateVolumeSquaresUI(0)
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
