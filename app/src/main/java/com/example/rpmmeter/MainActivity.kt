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
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp

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

        sharedPreferences = getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)
        loadSettings()

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.parseColor("#121212"))
        scrollView.isFillViewport = true

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(16, 16, 16, 16)
        mainLayout.gravity = Gravity.CENTER_HORIZONTAL

        mainLayout.addView(createTopPanel())

        statusText = TextView(this)
        statusText.text = "Ожидание запуска мотора..."
        statusText.textSize = 13f
        statusText.setTextColor(Color.LTGRAY)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 8, 0, 0)
        mainLayout.addView(statusText)

        debugText = TextView(this)
        debugText.text = "Громкость: 0 | Частота: 0 Гц"
        debugText.textSize = 11f
        debugText.setTextColor(Color.YELLOW)
        debugText.gravity = Gravity.CENTER
        debugText.setPadding(0, 2, 0, 12)
        mainLayout.addView(debugText)

        mainLayout.addView(createSettingsTable())

        val copyrightView = TextView(this)
        copyrightView.text = copyrightNotice
        copyrightView.textSize = 12f
        copyrightView.setTextColor(Color.parseColor("#9E9E9E"))
        copyrightView.gravity = Gravity.CENTER
        copyrightView.setPadding(16, 20, 16, 12)
        mainLayout.addView(copyrightView)

        scrollView.addView(mainLayout)
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

    private fun createTopPanel(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setPadding(0, 8, 0, 0)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val leftSpacer = View(this)
        leftSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 0.2f)

        val centerLayout = LinearLayout(this)
        centerLayout.orientation = LinearLayout.VERTICAL
        centerLayout.gravity = Gravity.CENTER_HORIZONTAL
        centerLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)

        rpmText = TextView(this)
        rpmText.text = "0"
        rpmText.textSize = 64f
        rpmText.setTextColor(Color.parseColor("#00E676"))
        rpmText.gravity = Gravity.CENTER

        val labelRpm = TextView(this)
        labelRpm.text = "RPM"
        labelRpm.textSize = 15f
        labelRpm.setTextColor(Color.parseColor("#80CBC4"))
        labelRpm.gravity = Gravity.CENTER
        labelRpm.setPadding(0, 0, 0, 2)

        centerLayout.addView(rpmText)
        centerLayout.addView(labelRpm)

        val rightLayout = LinearLayout(this)
        rightLayout.orientation = LinearLayout.VERTICAL
        rightLayout.gravity = Gravity.CENTER_HORIZONTAL
        rightLayout.layoutParams = LinearLayout.LayoutParams(0, 110, 0.2f)

        btnHold = Button(this)
        btnHold.text = "HOLD"
        btnHold.textSize = 11f
        btnHold.setOnClickListener {
            isHoldActive = !isHoldActive
            if (isHoldActive) {
                heldRpmValue = currentRealRpm
            }
            updateHoldButtonState()
        }
        
        val holdParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        holdParams.setMargins(4, 0, 0, 0)
        btnHold.layoutParams = holdParams
        updateHoldButtonState()
        
        rightLayout.addView(btnHold)

        container.addView(leftSpacer)
        container.addView(centerLayout)
        container.addView(rightLayout)

        return container
    }

    private fun createSettingsTable(): View {
        val tableLayout = TableLayout(this)
        tableLayout.setPadding(0, 4, 0, 0)
        tableLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        btn2T = Button(this)
        btn2T.text = "2T"
        btn2T.setOnClickListener { setEngine(2) }

        btn4T = Button(this)
        btn4T.text = "4T"
        btn4T.setOnClickListener { setEngine(4) }

        btnElectro = Button(this)
        btnElectro.text = "Электро"
        btnElectro.setOnClickListener { setEngine(3) }
        
        btnLimit1 = Button(this)
        btnLimit1.text = "6k"
        btnLimit1.setOnClickListener { setLimit(6000) }

        btnLimit2 = Button(this)
        btnLimit2.text = "12k"
        btnLimit2.setOnClickListener { setLimit(12000) }

        btnLimit3 = Button(this)
        btnLimit3.text = "20k"
        btnLimit3.setOnClickListener { setLimit(20000) }

        btnRateFast = Button(this)
        btnRateFast.text = "Fast"
        btnRateFast.setOnClickListener { setRate(1536) }

        btnRateNorm = Button(this)
        btnRateNorm.text = "Norm"
        btnRateNorm.setOnClickListener { setRate(2560) }

        btnRateSlow = Button(this)
        btnRateSlow.text = "Slow"
        btnRateSlow.setOnClickListener { setRate(4096) }

        btnSmoothSharp = Button(this)
        btnSmoothSharp.text = "Sharp"
        btnSmoothSharp.setOnClickListener { setSmooth(0.02f, 0.05f) }

        btnSmoothNorm = Button(this)
        btnSmoothNorm.text = "Norm"
        btnSmoothNorm.setOnClickListener { setSmooth(0.06f, 0.18f) }

        btnSmoothSoft = Button(this)
        btnSmoothSoft.text = "Soft"
        btnSmoothSoft.setOnClickListener { setSmooth(0.15f, 0.40f) }

        val engineRow = TableRow(this)
        engineRow.setPadding(0, 3, 0, 3)

        val engineLabel = TextView(this)
        engineLabel.text = "мотор:"
        engineLabel.textSize = 12f
        engineLabel.setTextColor(Color.parseColor("#B0BEC5"))
        engineLabel.setPadding(0, 0, 8, 0)

        val engineButtonsLayout = LinearLayout(this)
        engineButtonsLayout.orientation = LinearLayout.HORIZONTAL
        engineButtonsLayout.gravity = Gravity.CENTER

        val thirdParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        thirdParams.setMargins(2, 0, 2, 0)

        btn2T.layoutParams = thirdParams
        btn4T.layoutParams = thirdParams
        btnElectro.layoutParams = thirdParams
        
        engineButtonsLayout.addView(btn2T)
        engineButtonsLayout.addView(btn4T)
        engineButtonsLayout.addView(btnElectro)
        engineButtonsLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)
        
        engineRow.addView(engineLabel)
        engineRow.addView(engineButtonsLayout)
        tableLayout.addView(engineRow)

        addSettingRowToTable(tableLayout, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addSettingRowToTable(tableLayout, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addSettingRowToTable(tableLayout, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        return tableLayout
    }

    private fun addSettingRowToTable(table: TableLayout, labelTxt: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, 3, 0, 3)

        val label = TextView(this)
        label.text = labelTxt
        label.textSize = 12f
        label.setTextColor(Color.parseColor("#B0BEC5"))
        label.setPadding(0, 0, 8, 0)

        val buttonsLayout = LinearLayout(this)
        buttonsLayout.orientation = LinearLayout.HORIZONTAL
        buttonsLayout.gravity = Gravity.CENTER
        
        val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        params.setMargins(2, 0, 2, 0)

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
        table.addView(row)
    }

    private fun updateHoldButtonState() {
        if (::btnHold.isInitialized) {
            if (isHoldActive) {
                btnHold.setBackgroundColor(Color.parseColor("#FF9800"))
                btnHold.setTextColor(Color.BLACK)
            } else {
                btnHold.setBackgroundColor(Color.parseColor("#424242"))
                btnHold.setTextColor(Color.WHITE)
            }
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
        if (::btn2T.isInitialized) {
            btn2T.setBackgroundColor(if (engineType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
            btn2T.setTextColor(if (engineType == 2) Color.BLACK else Color.WHITE)
            
            btn4T.setBackgroundColor(if (engineType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
            btn4T.setTextColor(if (engineType == 4) Color.BLACK else Color.WHITE)

            btnElectro.setBackgroundColor(if (engineType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
            btnElectro.setTextColor(if (engineType == 3) Color.BLACK else Color.WHITE)
        }
    }

    private fun updateLimitButtons() {
        if (::btnLimit1.isInitialized) {
            btnLimit1.setBackgroundColor(if (maxAllowedRpm == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
            btnLimit2.setBackgroundColor(if (maxAllowedRpm == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
            btnLimit3.setBackgroundColor(if (maxAllowedRpm == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
            btnLimit1.setTextColor(Color.WHITE)
            btnLimit2.setTextColor(Color.WHITE)
            btnLimit3.setTextColor(Color.WHITE)
        }
    }

    private fun updateRateButtons() {
        if (::btnRateFast.isInitialized) {
            btnRateFast.setBackgroundColor(if (audioBufferSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
            btnRateNorm.setBackgroundColor(if (audioBufferSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
            btnRateSlow.setBackgroundColor(if (audioBufferSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
            btnRateFast.setTextColor(Color.WHITE)
            btnRateNorm.setTextColor(Color.WHITE)
            btnRateSlow.setTextColor(Color.WHITE)
        }
    }

    private fun updateSmoothButtons() {
        if (::btnSmoothSharp.isInitialized) {
            val isSharp = (riseTimeConstant == 0.02f)
            val isNorm = (riseTimeConstant == 0.06f)
            btnSmoothSharp.setBackgroundColor(if (isSharp) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
            btnSmoothNorm.setBackgroundColor(if (isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
            btnSmoothSoft.setBackgroundColor(if (!isSharp && !isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
            btnSmoothSharp.setTextColor(Color.WHITE)
            btnSmoothNorm.setTextColor(Color.WHITE)
            btnSmoothSoft.setTextColor(Color.WHITE)
        }
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

                    audioRecord.startRecording()
                    processAudioStream(audioRecord, sampleRate, currentBufferSz)
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            } finally {
                safeStopAndRelease(audioRecord)
            }
        }
    }

    private fun processAudioStream(audioRecord: AudioRecord, sampleRate: Int, currentBufferSz: Int) {
        val actualBuffer = ShortArray(currentBufferSz)
        var smoothedRpm = 0f
        val dt = currentBufferSz.toFloat() / sampleRate.toFloat()

        while (isRecording && audioBufferSize == currentBufferSz) {
            val readSize = audioRecord.read(actualBuffer, 0, currentBufferSz)
            if (readSize <= 0) continue

            var volume: Long = 0
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
                var maxCorrelation: Long = 0

                var lag = logMinLag
                while (lag <= logMaxLag) {
                    var correlation: Long = 0
                    val limit = readSize - lag
                    var i = 0
                    while (i < limit) {
                        correlation += actualBuffer[i].toLong() * actualBuffer[i + lag].toLong()
                        i++
                    }
                    if (correlation > maxCorrelation) {
                        maxCorrelation = correlation
                        bestLag = lag
                    }
                    lag++
                }

                if (bestLag > 0) {
                    dominantFreq = sampleRate.toFloat() / bestLag
                    
                    val calculatedRpm = when (engineType) {
                        4 -> (dominantFreq * 120).toInt()
                        3 -> (dominantFreq * 60).toInt()
                        else -> (dominantFreq * 60).toInt()
                    }

                    if (calculatedRpm in 500..maxAllowedRpm) {
                        rawRpm = calculatedRpm
                    }
                }
            }

            if (rawRpm > 0) {
                if (smoothedRpm == 0f) {
                    smoothedRpm = rawRpm.toFloat()
    
