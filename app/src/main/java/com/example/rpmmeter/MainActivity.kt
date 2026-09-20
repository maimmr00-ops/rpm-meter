package com.example.rpmmeter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var rpmText: TextView
    private lateinit var debugText: TextView
    private lateinit var btnHold: Button
    private lateinit var btnExit: Button
    
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button
    private lateinit var btnLimit1: Button
    private lateinit var btnLimit2: Button
    private lateinit var btnLimit3: Button
    
    private lateinit var btnRateFast: Button
    private lateinit var btnRateNorm: Button
    private lateinit var btnRateSlow: Button

    private lateinit var btnSmoothSharp: Button
    private lateinit var btnSmoothNorm: Button
    private lateinit var btnSmoothSoft: Button

    private var isRecording = false
    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentRealRpm = 0

    private var engineType = 2
    private var maxAllowedRpm = 12000
    private val volumeThreshold = 30

    private var audioBufferSize = 1536 
    private var riseTimeConstant = 0.06f 
    private var dropTimeConstant = 0.18f

    private lateinit var sharedPreferences: SharedPreferences
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    private val copyrightNotice = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 0.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        verifyLicenseOrCrash()

        sharedPreferences = getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)
        loadSettings()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- ВЕРХНЯЯ ЧАСТЬ: Обороты, HOLD и кнопка Выхода ---

        val topRpmLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        rpmText = TextView(this).apply {
            text = "0 000"
            textSize = 72f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
        }
        
        btnHold = Button(this).apply {
            text = "HOLD"
            textSize = 13f
            setOnClickListener {
                isHoldActive = !isHoldActive
                if (isHoldActive) {
                    heldRpmValue = currentRealRpm
                }
                updateHoldButtonState()
            }
        }
        
        val holdParams = LinearLayout.LayoutParams(
            160, 
            110  
        ).apply {
            setMargins(24, 0, 16, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        btnHold.layoutParams = holdParams
        updateHoldButtonState()

        btnExit = Button(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundColor(Color.parseColor("#424242"))
            setOnClickListener {
                finishAffinity()
            }
        }
        val exitParams = LinearLayout.LayoutParams(
            110,
            110
        ).apply {
            setMargins(8, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        btnExit.layoutParams = exitParams

        topRpmLayout.addView(rpmText)
        topRpmLayout.addView(btnHold)
        topRpmLayout.addView(btnExit)
        layout.addView(topRpmLayout)

        val labelRpmText = TextView(this).apply {
            text = "RPM"
            textSize = 15f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 2)
        }
        layout.addView(labelRpmText)

        statusText = TextView(this).apply {
            text = "Ожидание запуска мотора..."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 12)
        }
        layout.addView(debugText)


        // --- НИЖНЯЯ ЧАСТЬ: Кнопки и настройки (Сетка) ---

        val tableLayout = TableLayout(this).apply {
            setPadding(0, 4, 0, 0)
        }

        fun addSettingRow(labelTxt: String, b1: Button, b2: Button, b3: Button) {
            val row = TableRow(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 3, 0, 3)
            }

            val label = TextView(this).apply {
                text = labelTxt
                textSize = 12f
                setTextColor(Color.parseColor("#B0BEC5"))
                setPadding(0, 0, 8, 0)
            }

            val buttonsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            
            val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 0, 2, 0)
            }

            b1.layoutParams = params
            b2.layoutParams = params
            b3.layoutParams = params

            buttonsLayout.addView(b1)
            buttonsLayout.addView(b2)
            buttonsLayout.addView(b3)

            val wrapperParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
            buttonsLayout.layoutParams = wrapperParams

            row.addView(label)
            row.addView(buttonsLayout)
            tableLayout.addView(row)
        }

        btn2T = Button(this).apply { text = "2T"; setOnClickListener { setEngine(2) } }
        btn4T = Button(this).apply { text = "4T"; setOnClickListener { setEngine(4) } }
        
        btnLimit1 = Button(this).apply { text = "6k"; setOnClickListener { setLimit(6000) } }
        btnLimit2 = Button(this).apply { text = "12k"; setOnClickListener { setLimit(12000) } }
        btnLimit3 = Button(this).apply { text = "20k"; setOnClickListener { setLimit(20000) } }

        btnRateFast = Button(this).apply { text = "Fast"; setOnClickListener { setRate(1536) } }
        btnRateNorm = Button(this).apply { text = "Norm"; setOnClickListener { setRate(2560) } }
        btnRateSlow = Button(this).apply { text = "Slow"; setOnClickListener { setRate(4096) } }

        btnSmoothSharp = Button(this).apply { 
            text = "Sharp"
            setOnClickListener { setSmooth(0.02f, 0.05f) }
        }
        btnSmoothNorm = Button(this).apply { 
            text = "Norm"
            setOnClickListener { setSmooth(0.06f, 0.18f) }
        }
        btnSmoothSoft = Button(this).apply { 
            text = "Soft"
            setOnClickListener { setSmooth(0.15f, 0.40f) }
        }

        val engineRow = TableRow(this).apply { setPadding(0, 3, 0, 3) }
        val engineLabel = TextView(this).apply {
            text = "двигатель:"
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }
        val engineButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val halfParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }
        btn2T.layoutParams = halfParams
        btn4T.layoutParams = halfParams
        engineButtonsLayout.addView(btn2T)
        engineButtonsLayout.addView(btn4T)
        engineButtonsLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
        engineRow.addView(engineLabel)
        engineRow.addView(engineButtonsLayout)
        tableLayout.addView(engineRow)

        addSettingRow("лимит:", btnLimit1, btnLimit2, btnLimit3)
        addSettingRow("обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addSettingRow("плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        layout.addView(tableLayout)

        val copyrightView = TextView(this).apply {
            text = copyrightNotice
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 12)
        }
        layout.addView(copyrightView)

        scrollView.addView(layout)
        setContentView(scrollView)

        updateEngineButtons()
        updateLimitButtons()
        updateRateButtons()
        updateSmoothButtons()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startAudioThread()
        }
    }

    private fun verifyLicenseOrCrash() {
        if (copyrightNotice.length != 43) {
            throw RuntimeException("License Error: Length mismatch!")
        }
        if (copyrightNotice[0] != '2' || 
            copyrightNotice[8] != 'Y' || 
            copyrightNotice[18] != 'V' || 
            copyrightNotice[26] != 'Р' || 
            copyrightNotice[42] != '1') {
            throw RuntimeException("License Error: Integrity violation!")
        }
    }

    private fun updateHoldButtonState() {
        if (isHoldActive) {
            btnHold.setBackgroundColor(Color.parseColor("#FF9800"))
            btnHold.setTextColor(Color.BLACK)
        } else {
            btnHold.setBackgroundColor(Color.parseColor("#424242"))
            btnHold.setTextColor(Color.WHITE)
        }
    }

    private fun loadSettings() {
        engineType = sharedPreferences.getInt("engineType", 2)
        maxAllowedRpm = sharedPreferences.getInt("maxAllowedRpm", 12000)
        audioBufferSize = sharedPreferences.getInt("audioBufferSize", 1536)
        riseTimeConstant = sharedPreferences.getFloat("riseTimeConstant", 0.06f)
        dropTimeConstant = sharedPreferences.getFloat("dropTimeConstant", 0.18f)
    }

    private fun setEngine(type: Int) {
        engineType = type
        sharedPreferences.edit().putInt("engineType", type).apply()
        updateEngineButtons()
    }

    private fun setLimit(limit: Int) {
        maxAllowedRpm = limit
        sharedPreferences.edit().putInt("maxAllowedRpm", limit).apply()
        updateLimitButtons()
    }

    private fun setRate(size: Int) {
        audioBufferSize = size
        sharedPreferences.edit().putInt("audioBufferSize", size).apply()
        updateRateButtons()
    }

    private fun setSmooth(rise: Float, drop: Float) {
        riseTimeConstant = rise
        dropTimeConstant = drop
        sharedPreferences.edit().putFloat("riseTimeConstant", rise).putFloat("dropTimeConstant", drop).apply()
        updateSmoothButtons()
    }

    private fun updateEngineButtons() {
        btn2T.setBackgroundColor(if (engineType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn2T.setTextColor(if (engineType == 2) Color.BLACK else Color.WHITE)
        btn4T.setBackgroundColor(if (engineType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn4T.setTextColor(if (engineType == 4) Color.BLACK else Color.WHITE)
    }

    private fun updateLimitButtons() {
        btnLimit1.setBackgroundColor(if (maxAllowedRpm == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit2.setBackgroundColor(if (maxAllowedRpm == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit3.setBackgroundColor(if (maxAllowedRpm == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit1.setTextColor(Color.WHITE); btnLimit2.setTextColor(Color.WHITE); btnLimit3.setTextColor(Color.WHITE)
    }

    private fun updateRateButtons() {
        btnRateFast.setBackgroundColor(if (audioBufferSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateNorm.setBackgroundColor(if (audioBufferSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateSlow.setBackgroundColor(if (audioBufferSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateFast.setTextColor(Color.WHITE); btnRateNorm.setTextColor(Color.WHITE); btnRateSlow.setTextColor(Color.WHITE)
    }

    private fun updateSmoothButtons() {
        val isSharp = (riseTimeConstant == 0.02f)
        val isNorm = (riseTimeConstant == 0.06f)
        btnSmoothSharp.setBackgroundColor(if (isSharp) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothNorm.setBackgroundColor(if (isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSoft.setBackgroundColor(if (!isSharp && !isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSharp.setTextColor(Color.WHITE); btnSmoothNorm.setTextColor(Color.WHITE); btnSmoothSoft.setTextColor(Color.WHITE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioThread()
        }
    }

    private fun safeStopAndRelease(recorder: AudioRecord?) {
        try {
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder.stop()
                recorder.release()
            }
        } catch (_: Exception) {}
    }

    private fun startAudioThread() {
        isRecording = true
        thread {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            var audioRecord: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                if (minBuf <= 0) {
                    runOnUiThread { statusText.text = "Ошибка: Микрофон не поддерживается" }
                    return@thread
                }

                while (isRecording) {
                    val currentBufferSz = maxOf(minBuf, audioBufferSize)

                    safeStopAndRelease(audioRecord)

                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        currentBufferSz
                    )

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        runOnUiThread { statusText.text = "Ошибка инициализации микрофона" }
                        Thread.sleep(1000)
                        continue
                    }

                    val actualBuffer = ShortArray(currentBufferSz)
                    audioRecord.startRecording()
                    var smoothedRpm = 0f

                    val dt = currentBufferSz.toFloat() / sampleRate.toFloat()

                    while (isRecording && audioBufferSize == currentBufferSz) {
                        val readSize = audioRecord.read(actualBuffer, 0, currentBufferSz)
                        if (readSize > 0) {
                            var volume = 0L
                            for (i in 0 until readSize) {
                                volume += abs(actualBuffer[i].toLong())
                            }
                            val avgVolume = (volume / readSize).toInt()

                            var rawRpm = 0
                            var dominantFreq = 0f

                            if (avgVolume > volumeThreshold) {
                                val logMinLag = sampleRate / 200
                                val logMaxLag = sampleRate / 15
                                
                                var bestLag = -1
                                var maxCorrelation = 0L

                                for (lag in logMinLag..logMaxLag) {
                                    var correlation = 0L
                                    val limit = readSize - lag
                                    for (i in 0 until limit) {
                                        correlation += (actualBuffer[i].toLong() * actualBuffer[i + lag].toLong())
                                    }
                                    if (correlation > maxCorrelation) {
                                       maxCorrelation = correlation
                                       bestLag = lag
                                    }
                                }

                                if (bestLag > 0) {
                                    dominantFreq = sampleRate.toFloat() / bestLag
                                    val calculatedRpm = if (engineType == 2) {
                                        (dominantFreq * 60).toInt()
                                    } else {
                                        (dominantFreq * 120).toInt()
                                    }

                                    if (calculatedRpm in 500..maxAllowedRpm) {
                                        rawRpm = calculatedRpm
                                    }
                                }
                            }

                            if (rawRpm > 0) {
                                if (smoothedRpm == 0f) {
                                    smoothedRpm = rawRpm.toFloat()
                                } else {
                                    val alpha = 1f - (-dt / riseTimeConstant).toDouble().let { kotlin.math.exp(it) }.toFloat()
                                    smoothedRpm = smoothedRpm + alpha * (rawRpm - smoothedRpm)
                                }
                            } else {
                                val dropAlpha = 1f - (-dt / dropTimeConstant).toDouble().let { kotlin.math.exp(it) }.toFloat()
                                smoothedRpm = smoothedRpm * (1f - dropAlpha)
                                if (smoothedRpm < 300) smoothedRpm = 0f
                            }

                            val finalRpm = smoothedRpm.toInt()
                            currentRealRpm = finalRpm

                            runOnUiThread {
                                debugText.text = "Громкость: $avgVolume | Частота: ${dominantFreq.toInt()} Гц"
                                
                                if (isHoldActive) {
                                    val displayHoldVal = if (heldRpmValue > 0) heldRpmValue else 0
                                    rpmText.text = String.format("%,d", displayHoldVal).replace(','
