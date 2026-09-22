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
    
    private var currentMultiplier = 1

    private lateinit var settings: UIBuilder.SettingsButtons
    private val volumeStepButtons = arrayOfNulls<Button>(10)

    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentRealRpm = 0

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    private val PERMISSION_CODE = 200

    // Имя текущего алгоритма и его индекс (для передачи в AudioAnalyzer)
    private var currentAlgorithmIndex = 0

    // Элементы новой строки алгоритмов
    private lateinit var tvAlgorithmModeLabel: TextView
    private val algorithmButtons = arrayOfNulls<Button>(4)

    // Переменные для инерции (плавности) цифр на экране
    private var displayedRpmFloat = 0f
    private var targetRpmFloat = 0f
    private var isAnimatingRpm = false

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

        settings = UIBuilder.buildSettingsTable(
            context = this,
            prefsManager = prefsManager,
            onRefreshUI = { 
                refreshAllUI()
            },
            volumeStepButtons = volumeStepButtons,
            onMultiplierChange = { mult ->
                currentMultiplier = mult
                refreshAllUI()
            },
            currentMultiplierGetter = { currentMultiplier }
        )

        rootLayout.addView(buildTopPanel())
        rootLayout.addView(buildInfoPanelWithSides())
        rootLayout.addView(settings.table)
        
        // Добавляем новую строку выбора алгоритмов под таблицей настроек
        rootLayout.addView(buildAlgorithmSelectionRow())

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

    // Создаем новую строку алгоритмов (слева текст режима, справа кнопки)
    private fun buildAlgorithmSelectionRow(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 6, 4, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Левая плашка (режим мотора / тип)
        tvAlgorithmModeLabel = TextView(this).apply {
            text = "режим: 2T"
            textSize = 12f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.35f).apply {
                setMargins(0, 0, 4, 0)
            }
        }
        container.addView(tvAlgorithmModeLabel)

        // Правая часть с кнопками алгоритмов
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.65f)
        }

        val btnParams = LinearLayout.LayoutParams(0, 40, 1f).apply {
            setMargins(2, 0, 2, 0)
        }

        for (i in 0 until 4) {
            val algButton = Button(this).apply {
                text = "Алг ${i + 1}"
                textSize = 10f
                setPadding(0, 0, 0, 0)
                layoutParams = btnParams
                setOnClickListener {
                    currentAlgorithmIndex = i
                    refreshAlgorithmButtonsUI()
                    restartAnalyzer()
                }
            }
            algorithmButtons[i] = algButton
            buttonsLayout.addView(algButton)
        }

        container.addView(buttonsLayout)
        return container
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
            onUpdate = { rpm, rawFreq, filteredFreq, vol, status ->
                currentRealRpm = if (currentMultiplier > 0) (rpm / currentMultiplier) else rpm
                
                runOnUiThread {
                    val currentThreshold = prefsManager.minVolumeThreshold
                    
                    val targetVal = if (isHoldActive) {
                        if (heldRpmValue > 0) heldRpmValue else 0
                    } else {
                        currentRealRpm
                    }
                    
                    // Передаем целевое значение в механизм плавной инерции цифр
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
                    statusLine3.text = "Pre-Freq: ${rawFreq.roundToInt()}Гц | All: ${filteredFreq.roundToInt()}Гц"

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

    // Плавное изменение цифр на экране с учетом пресетов Sharp / Norm / Soft
    private fun setTargetRpmSmooth(target: Float) {
        targetRpmFloat = target
        if (!isAnimatingRpm) {
            startRpmInertiaLoop()
        }
    }

    private fun startRpmInertiaLoop() {
        isAnimatingRpm = true
        rpmTextView.postDelayed(object : Runnable {
            override fun run() {
                val diff = targetRpmFloat - displayedRpmFloat
                val preset = prefsManager.smoothPreset // 0 - Sharp, 1 - Norm, 2 - Soft
                
                val smoothingFactor = when (preset) {
                    0 -> 1.0f  // Sharp: Мгновенно
                    1 -> if (targetRpmFloat < displayedRpmFloat) 0.2f else 0.4f // Norm
                    else -> if (targetRpmFloat < displayedRpmFloat) 0.08f else 0.25f // Soft
                }

                if (preset == 0) {
                    displayedRpmFloat = targetRpmFloat
                } else {
                    displayedRpmFloat += diff * smoothingFactor
                }

                updateRpmDisplay(displayedRpmFloat.toInt())

                if (kotlin.math.abs(diff) > 0.5f || targetRpmFloat > 0f) {
                    rpmTextView.postDelayed(this, 16L) // ~60 FPS обновление анимации цифр
                } else {
                    isAnimatingRpm = false
                }
            }
        }, 16L)
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
            setPadding(0, 0, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }

        btnExit = Button(this).apply {
            text = "EXIT"
            textSize = 12f
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        leftCol.addView(btnExit)
        container.addView(leftCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        val rpmBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
        }

        rpmTextView = TextView(this).apply {
            text = "00000"
            textSize = 82f
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

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
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
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        rightCol.addView(btnHold)
        container.addView(rightCol)

        return container
    }

    private fun buildInfoPanelWithSides(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val panelHeight = 44
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        val leftMultipliers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }

        settings.btnX1.apply { text = "/1"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 1; refreshAllUI() } }
        settings.btnX2.apply { text = "/2"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 2; refreshAllUI() } }

        (settings.btnX1.parent as? LinearLayout)?.removeView(settings.btnX1)
        (settings.btnX2.parent as? LinearLayout)?.removeView(settings.btnX2)

        leftMultipliers.addView(settings.btnX1)
        leftMultipliers.addView(settings.btnX2)
        container.addView(leftMultipliers)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        val centerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f)
        }

        statusLine1 = TextView(this).apply {
            text = "Ожидание запуска двигателя"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine1)

        statusLine2 = TextView(this).apply {
            text = "Громкость: 0 | Порог: 20"
            textSize = 10f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine2)

        statusLine3 = TextView(this).apply {
            text = "Pre-Freq: 0 Гц | All: 0 Гц"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine3)

        container.addView(centerTextCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        val rightMultipliers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }

        settings.btnX3.apply { text = "/3"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 3; refreshAllUI() } }
        settings.btnX4.apply { text = "/4"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 4; refreshAllUI() } }

        (settings.btnX3.parent as? LinearLayout)?.removeView(settings.btnX3)
        (settings.btnX4.parent as? LinearLayout)?.removeView(settings.btnX4)

        rightMultipliers.addView(settings.btnX3)
        rightMultipliers.addView(settings.btnX4)
        container.addView(rightMultipliers)

        return container
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

        settings.btnX1.setBackgroundColor(if (currentMultiplier == 1) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btnX1.setTextColor(if (currentMultiplier == 1) Color.BLACK else Color.WHITE)
        settings.btnX2.setBackgroundColor(if (currentMultiplier == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btnX2.setTextColor(if (currentMultiplier == 2) Color.BLACK else Color.WHITE)
        settings.btnX3.setBackgroundColor(if (currentMultiplier == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btnX3.setTextColor(if (currentMultiplier == 3) Color.BLACK else Color.WHITE)
        settings.btnX4.setBackgroundColor(if (currentMultiplier == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        settings.btnX4.setTextColor(if (currentMultiplier == 4) Color.BLACK else Color.WHITE)

        val eType = prefsManager
