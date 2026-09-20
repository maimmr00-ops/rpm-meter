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

    private lateinit var statusText: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var btnHold: Button
    private lateinit var btnExit: Button
    
    private lateinit var btnX1: Button
    private lateinit var btnX2: Button
    private lateinit var btnX3: Button
    private lateinit var btnX4: Button
    private var currentMultiplier = 1

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

        statusText = TextView(this).apply {
            text = "Ожидание запуска двигателя (тихо)"
            textSize = 12f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        rootLayout.addView(statusText)

        // Подключаем таблицу настроек через UIBuilder
        rootLayout.addView(
            UIBuilder.buildSettingsTable(this, prefsManager, { refreshAllUI() }, volumeStepButtons)
        )

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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }

        btnExit = Button(this).apply {
            text = "EXIT"
            textSize = 11f
            setOnClickListener { finish() }
        }
        val pBtn = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, 1, 0, 1)
        }
        btnExit.layoutParams = pBtn
        leftCol.addView(btnExit)

        val subLeft = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = pBtn
        }

        val pSub = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        btnX1 = Button(this).apply {
            text = "x1"; textSize = 10f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 1; refreshAllUI() }
            layoutParams = pSub
        }
        subLeft.addView(btnX1)

        btnX2 = Button(this).apply {
            text = "x2"; textSize = 10f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 2; refreshAllUI() }
            layoutParams = pSub
        }
        subLeft.addView(btnX2)

        leftCol.addView(subLeft)
        container.addView(leftCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(6, 1) })

        val rpmBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
        }

        rpmTextView = TextView(this).apply {
            text = "00000"
            textSize = 68f
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

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(6, 1) })

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }

        btnHold = Button(this).apply {
            text = "HOLD"
            textSize = 11f
            setOnClickListener {
                isHoldActive = !isHoldActive
                if (isHoldActive) heldRpmValue = currentRealRpm
                refreshAllUI()
            }
            layoutParams = pBtn
        }
        rightCol.addView(btnHold)

        val subRight = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = pBtn
        }

        btnX3 = Button(this).apply {
            text = "x3"; textSize = 10f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 3; refreshAllUI() }
            layoutParams = pSub
        }
        subRight.addView(btnX3)

        btnX4 = Button(this).apply {
            text = "x4"; textSize = 10f; setPadding(0,0,0,0)
            setOnClickListener { currentMultiplier = 4; refreshAllUI() }
            layoutParams = pSub
        }
        subRight.addView(btnX4)

        rightCol.addView(subRight)
        container.addView(rightCol)

        return container
    }

    private fun refreshAllUI() {
        if (!::btnHold.isInitialized) return

        btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)

        btnX1.setBackgroundColor(if (currentMultiplier == 1) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX1.setTextColor(if (currentMultiplier == 1) Color.BLACK else Color.WHITE)
        btnX2.setBackgroundColor(if (currentMultiplier == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX2.setTextColor(if (currentMultiplier == 2) Color.BLACK else Color.WHITE)
        btnX3.setBackgroundColor(if (currentMultiplier == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX3.setTextColor(if (currentMultiplier == 3) Color.BLACK else Color.WHITE)
        btnX4.setBackgroundColor(if (currentMultiplier == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnX4.setTextColor(if (currentMultiplier == 4) Color.BLACK else Color.WHITE)

        updateVolumeSquaresUI(0)
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

