package com.example.rpmmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
    
    private var currentMultiplier = 1

    private lateinit var settings: UIBuilder.SettingsButtons
    private val volumeStepButtons = arrayOfNulls<Button>(10)

    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentRealRpm = 0

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    private val PERMISSION_CODE = 200

    private var currentAlgorithmIndex = 0
    private lateinit var tvAlgorithmModeLabel: TextView
    private val algorithmButtons = arrayOfNulls<Button>(4)

    private var displayedRpmFloat = 0f
    private var targetRpmFloat = 0f
    private var isAnimatingRpm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        prefsManager = PreferencesManager(this)
        currentAlgorithmIndex = prefsManager.algorithmIndex

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        settings = UIBuilder.buildSettingsTable(
            context = this,
            prefsManager = prefsManager,
            onRefreshUI = { refreshAllUI() },
            volumeStepButtons = volumeStepButtons,
            onMultiplierChange = { mult -> currentMultiplier = mult; refreshAllUI() },
            currentMultiplierGetter = { currentMultiplier }
        )

        val header = HeaderBuilder.buildAll(
            context = this,
            onExitClick = { finish() },
            onHoldClick = {
                isHoldActive = !isHoldActive
                if (isHoldActive) heldRpmValue = currentRealRpm
                refreshAllUI()
            },
            onMultiplierClick = { mult -> currentMultiplier = mult; refreshAllUI() },
            onAlgorithmClick = { idx ->
                currentAlgorithmIndex = idx
                prefsManager.algorithmIndex = idx
                refreshAlgorithmButtonsUI()
                restartAnalyzer()
            },
            settings = settings
        )

        rpmTextView = header.rpmTextView
        btnHold = header.btnHold
        btnExit = header.btnExit
        tvAlgorithmModeLabel = header.tvAlgorithmModeLabel
        for (i in 0..3) { algorithmButtons[i] = header.algorithmButtons[i] }

        statusLine1 = header.statusLine1
        statusLine2 = header.statusLine2
        statusLine3 = header.statusLine3

        rootLayout.addView(header.topPanel)
        rootLayout.addView(header.infoPanel)
        rootLayout.addView(settings.table)
        rootLayout.addView(header.algorithmRow)

        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.2"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 8)
        }
        rootLayout.addView(copyright)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAllUI()
        updateAlgorithmButtonsVisibility()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        } else {
            initAndStartAudioAnalyzer()
        }
    }

    private fun refreshAlgorithmButtonsUI() {
        val activeColor = Color.parseColor("#00838F")
        val defaultColor = Color.parseColor("#424242")
        for (i in 0 until 4) {
            algorithmButtons[i]?.setBackgroundColor(if (i == currentAlgorithmIndex) activeColor else defaultColor)
            algorithmButtons[i]?.setTextColor(Color.WHITE)
        }
    }

    private fun updateAlgorithmButtonsVisibility() {
        val eType = prefsManager.engineType
        tvAlgorithmModeLabel.text = when (eType) {
            2 -> "режим: 2T"
            4 -> "режим: 4T"
            else -> "режим: Others"
        }
        refreshAlgorithmButtonsUI()
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
            selectedAlgorithmIndex = currentAlgorithmIndex,
            onUpdate = { rpm, freq, vol, status ->
                currentRealRpm = if (currentMultiplier > 0) (rpm / currentMultiplier) else rpm
                runOnUiThread {
                    val currentThreshold = prefsManager.minVolumeThreshold
                    val targetVal = if (isHoldActive) (if (heldRpmValue > 0) heldRpmValue else 0) else currentRealRpm
                    
                    setTargetRpmSmooth(targetVal.toFloat())
                    
                    if (isHoldActive) {
                        statusLine1.text = "HOLD. Текущие: $currentRealRpm об/мин"
                        statusLine1.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        if (vol < currentThreshold) {
                            statusLine1.text = "Ожидание запуска двигателя"
                            statusLine1.setTextColor(Color.YELLOW)
                        } else {
                            statusLine1.text = "Работа мотора"
                            statusLine1.setTextColor(Color.parseColor("#00E676"))
                        }
                    }

                    statusLine2.text = "Громкость: $vol | Порог: $currentThreshold"
                    statusLine3.text = "Частота: ${freq.roundToInt()} Гц | Статус: $status"
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
        audioAnalyzer?.start()
    }

    private fun setTargetRpmSmooth(target: Float) {
        targetRpmFloat = target
        if (!isAnimatingRpm) startRpmInertiaLoop()
    }

    private fun startRpmInertiaLoop() {
        isAnimatingRpm = true
        rpmTextView.postDelayed(object : Runnable {
            override fun run() {
                val diff = targetRpmFloat - displayedRpmFloat
                val preset = prefsManager.smoothPreset
                val smoothingFactor = when (preset) {
                    0 -> 1.0f
                    1 -> if (targetRpmFloat < displayedRpmFloat) 0.2f else 0.4f
                    else -> if (targetRpmFloat < displayedRpmFloat) 0.08f else 0.25f
                }

                if (preset == 0) displayedRpmFloat = targetRpmFloat
                else displayedRpmFloat += diff * smoothingFactor

                updateRpmDisplay(displayedRpmFloat.toInt())

                if (kotlin.math.abs(diff) > 0.5f || targetRpmFloat > 0f) {
                    rpmTextView.postDelayed(this, 16L)
                } else {
                    isAnimatingRpm = false
                }
            }
        }, 16L)
    }

    private fun updateRpmDisplay(value: Int) {
        val clamped = value.coerceIn(0, 99999)
        rpmTextView.text = String.format("%5d", clamped).replace(' ', '\u00A0')
    }

    private fun updateVolumeSquaresUI(currentVol: Int) {
        val thresh = prefsManager.minVolumeThreshold
        var threshIdx = 0
        if (prefsManager.hasStoredThreshold()) {
            for (i in 0 until 10) if (UIBuilder.getThresholdForSquare(i) == thresh) { threshIdx = i; break }
        }
        var volIdx = -1
        if (currentVol > 0) {
            for (i in 9 downTo 0) if (currentVol >= UIBuilder.getThresholdForSquare(i)) { volIdx = i; break }
        }

        for (i in 0 until 10) {
            val btn = volumeStepButtons[i] ?: continue
            val color = when {
                i == threshIdx && volIdx >= i -> Color.parseColor("#00E676")
                i == threshIdx -> Color.parseColor("#FF9800")
                i < threshIdx && volIdx >= i -> Color.parseColor("#00BCD4")
                i > threshIdx && volIdx >= i -> Color.parseColor("#D0F8E8")
                else -> Color.parseColor("#37474F")
            }
            btn.setBackgroundColor(color)
        }
    }

    private fun refreshAllUI() {
        if (!::btnHold.isInitialized || !::settings.isInitialized) return

        btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)
        btnExit.setBackgroundColor(Color.parseColor("#424242"))
        btnExit.setTextColor(Color.WHITE)

        val mults = listOf(settings.btnX1 to 1, settings.btnX2 to 2, settings.btnX3 to 3, settings.btnX4 to 4)
        mults.forEach { (btn, m) ->
            btn.setBackgroundColor(if (currentMultiplier == m) Color.parseColor("#00E676") else Color.parseColor("#424242"))
            btn.setTextColor(if (currentMultiplier == m) Color.BLACK else Color.WHITE)
        }

        val eType = prefsManager.engineType
        val engines = listOf(settings.btn2T to 2, settings.btn4T to 4, settings.btnOthers to 3)
        engines.forEach { (btn, t) ->
            btn.setBackgroundColor(if (eType == t) Color.parseColor("#00E676") else Color.parseColor("#424242"))
            btn.setTextColor(if (eType == t) Color.BLACK else Color.WHITE)
        }

        val limit = prefsManager.maxAllowedRpm
        val limits = listOf(settings.btnLimit1 to 6000, settings.btnLimit2 to 12000, settings.btnLimit3 to 20000)
        limits.forEach { (btn, l) ->
            btn.setBackgroundColor(if (limit == l) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
            btn.setTextColor(Color.WHITE)
        }

        val bufSize = prefsManager.audioBufferSize
        val buffers = listOf(settings.btnRateFast to 1536, settings.btnRateNorm to 2560, settings.btnRateSlow to 4096)
        buffers.forEach { (btn, b) ->
            btn.setBackgroundColor(if (bufSize == b) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
            btn.setTextColor(Color.WHITE)
        }

        val preset = prefsManager.smoothPreset
        val smooths = listOf(settings.btnSmoothSharp to 0, settings.btnSmoothNorm to 1, settings.btnSmoothSoft to 2)
        smooths.forEach { (btn, p) ->
            btn.setBackgroundColor(if (preset == p) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
            btn.setTextColor(Color.WHITE)
        }

        updateAlgorithmButtonsVisibility()
        updateVolumeSquaresUI(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initAndStartAudioAnalyzer()
        }
    }

    override fun onDestroy() {
        audioAnalyzer?.stop()
        super.onDestroy()
    }
}
