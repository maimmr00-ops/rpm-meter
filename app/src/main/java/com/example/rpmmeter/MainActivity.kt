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
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder.SettingsButtons
    
    private val volumeStepButtons = arrayOfNulls<Button>(10)
    private var currentMultiplier = 1
    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentDisplayRpm = 0f
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
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        headerBuilder = HeaderBuilder(
            context = this,
            onExit = { finish() },
            onHoldToggle = {
                isHoldActive = !isHoldActive
                if (isHoldActive) {
                    heldRpmValue = currentDisplayRpm.roundToInt()
                }
                refreshAllUI()
            },
            onMultiplierSelect = { mult ->
                currentMultiplier = mult
                refreshAllUI()
            },
            onSmoothSelect = { preset ->
                prefsManager.smoothPreset = preset
                refreshAllUI()
            }
        )

        uiBuilder = UIBuilder.buildSettingsTable(
            context = this,
            prefsManager = prefsManager,
            onRefreshUI = { refreshAllUI() },
            volumeStepButtons = volumeStepButtons
        )

        rootLayout.addView(headerBuilder.buildTopPanel())
        rootLayout.addView(headerBuilder.buildInfoPanelWithSides())
        rootLayout.addView(uiBuilder.table)

        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.3"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 8)
        }
        rootLayout.addView(copyright)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAllUI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        } else {
            initAndStartAudioAnalyzer()
        }
    }

    fun restartAnalyzer() {
        audioAnalyzer?.stop()
        audioAnalyzer = null
        initAndStartAudioAnalyzer()
    }

    private fun initAndStartAudioAnalyzer() {
        audioAnalyzer?.stop()
        
        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            onUpdate = { rawRpm, allFreq, preFreq, vol, status ->
                // Делим на выбранный коэффициент (множитель цилиндров / формулы)
                val targetRpm = if (currentMultiplier > 0) (rawRpm / currentMultiplier) else rawRpm

                // Честная реализация плавности тахометра (Sharp = 0-1 фреймов, Norm = 2-5 фреймов, Soft = 8 фреймов)
                val preset = prefsManager.smoothPreset
                val smoothedRpm = when (preset) {
                    0 -> targetRpm // Sharp: мгновенно (0 промежуточных шагов)
                    1 -> currentDisplayRpm + (targetRpm - currentDisplayRpm) / 3.0f // Norm: плавные промежуточные шаги
                    else -> currentDisplayRpm + (targetRpm - currentDisplayRpm) / 7.0f // Soft: мягкое затухание скачков
                }
                currentDisplayRpm = smoothedRpm

                runOnUiThread {
                    val currentThreshold = prefsManager.minVolumeThreshold
                    val displayInt = if (isHoldActive) heldRpmValue else currentDisplayRpm.roundToInt()

                    if (isHoldActive) {
                        headerBuilder.statusLine1.text = "HOLD. Текущая частота: $displayInt rpm"
                        headerBuilder.statusLine1.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        if (vol < currentThreshold) {
                            headerBuilder.statusLine1.text = "Ожидание запуска двигателя"
                            headerBuilder.statusLine1.setTextColor(Color.YELLOW)
                        } else {
                            headerBuilder.statusLine1.text = status
                            headerBuilder.statusLine1.setTextColor(Color.parseColor("#00E676"))
                        }
                    }

                    headerBuilder.statusLine2.text = "Громкость: $vol | Порог: $currentThreshold"
                    headerBuilder.statusLine3.text = "All: ${allFreq.roundToInt()} Гц | Pre-Freq: ${preFreq.roundToInt()} Гц"

                    updateRpmDisplay(displayInt)
                    updateVolumeSquaresUI(vol)
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    headerBuilder.statusLine1.text = errorMsg
                    headerBuilder.statusLine1.setTextColor(Color.RED)
                }
            }
        )
        audioAnalyzer?.start()
    }

    private fun updateRpmDisplay(value: Int) {
        val clamped = value.coerceIn(0, 99999)
        val formatted = String.format("%5d", clamped).replace(' ', '\u00A0')
        headerBuilder.rpmTextView.text = formatted
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
                i == thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#00E676"))
                i == thresholdIndex -> btn.setBackgroundColor(Color.parseColor("#FF9800"))
                i < thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#00BCD4"))
                i > thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#D0F8E8"))
                else -> btn.setBackgroundColor(Color.parseColor("#37474F"))
            }
        }
    }

    private fun refreshAllUI() {
        if (!::headerBuilder.isInitialized || !::uiBuilder.isInitialized) return

        headerBuilder.btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        headerBuilder.btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)
        headerBuilder.btnExit.setBackgroundColor(Color.parseColor("#424242"))
        headerBuilder.btnExit.setTextColor(Color.WHITE)

        // Множители /1 - /4
        headerBuilder.btnX1.setBackgroundColor(if (currentMultiplier == 1) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX1.setTextColor(if (currentMultiplier == 1) Color.BLACK else Color.WHITE)
        headerBuilder.btnX2.setBackgroundColor(if (currentMultiplier == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX2.setTextColor(if (currentMultiplier == 2) Color.BLACK else Color.WHITE)
        headerBuilder.btnX3.setBackgroundColor(if (currentMultiplier == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX3.setTextColor(if (currentMultiplier == 3) Color.BLACK else Color.WHITE)
        headerBuilder.btnX4.setBackgroundColor(if (currentMultiplier == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX4.setTextColor(if (currentMultiplier == 4) Color.BLACK else Color.WHITE)

        // Кнопки плавности в хидере
        val preset = prefsManager.smoothPreset
        headerBuilder.btnSmoothSharp.setBackgroundColor(if (preset == 0) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        headerBuilder.btnSmoothNorm.setBackgroundColor(if (preset == 1) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        headerBuilder.btnSmoothSoft.setBackgroundColor(if (preset == 2) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        listOf(headerBuilder.btnSmoothSharp, headerBuilder.btnSmoothNorm, headerBuilder.btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }

        // Тип мотора
        val eType = prefsManager.engineType
        uiBuilder.btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        uiBuilder.btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        uiBuilder.btnOthers.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btnOthers.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

        // Лимиты RPM
        val limit = prefsManager.maxAllowedRpm
        uiBuilder.btnLimit1.setBackgroundColor(if (limit == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        uiBuilder.btnLimit2.setBackgroundColor(if (limit == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        uiBuilder.btnLimit3.setBackgroundColor(if (limit == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnLimit1, uiBuilder.btnLimit2, uiBuilder.btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        // Скорость буфера
        val bufSize = prefsManager.audioBufferSize
        uiBuilder.btnRateFast.setBackgroundColor(if (bufSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        uiBuilder.btnRateNorm.setBackgroundColor(if (bufSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        uiBuilder.btnRateSlow.setBackgroundColor(if (bufSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnRateFast, uiBuilder.btnRateNorm, uiBuilder.btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        // Алгоритмы
        val alg = prefsManager.algorithmIndex
        uiBuilder.btnAlg1.setBackgroundColor(if (alg == 0) Color.parseColor("#00BCD4") else Color.parseColor("#424242"))
        uiBuilder.btnAlg2.setBackgroundColor(if (alg == 1) Color.parseColor("#00BCD4") else Color.parseColor("#424242"))
        uiBuilder.btnAlg3.setBackgroundColor(if (alg == 2) Color.parseColor("#00BCD4") else Color.parseColor("#424242"))
        uiBuilder.btnAlg4.setBackgroundColor(if (alg == 3) Color.parseColor("#00BCD4") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnAlg1, uiBuilder.btnAlg2, uiBuilder.btnAlg3, uiBuilder.btnAlg4).forEach { it.setTextColor(Color.WHITE) }
        
        updateVolumeSquaresUI(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initAndStartAudioAnalyzer()
        }
    }

    override fun onDestroy() {
        audioAnalyzer?.stop()
        super.onDestroy()
    }
}
