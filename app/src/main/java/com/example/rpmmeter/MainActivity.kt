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

    private lateinit var prefs: SharedPreferences
    private val PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        prefs = getSharedPreferences("RpmPrefs", Context.MODE_PRIVATE)
        engineType = prefs.getInt("engineType", 2)
        maxAllowedRpm = prefs.getInt("maxAllowedRpm", 12000)
        audioBufferSize = prefs.getInt("audioBufferSize", 1536)
        riseTimeConstant = prefs.getFloat("riseTimeConstant", 0.06f)
        dropTimeConstant = prefs.getFloat("dropTimeConstant", 0.18f)

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
            text = "Ожидание запуска мотора..."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        rootLayout.addView(statusText)

        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 12)
        }
        rootLayout.addView(debugText)

        rootLayout.addView(buildSettingsTable())

        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 0.1"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 12)
        }
        rootLayout.addView(copyright)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAllUI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        } else {
            startAudioThread()
        }
    }

    private fun buildTopPanel(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 0.2f) })

        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }

        rpmText = TextView(this).apply {
            text = "0"
            textSize = 64f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
        }
        centerCol.addView(rpmText)

        val rpmLabel = TextView(this).apply {
            text = "RPM"
            textSize = 15f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 2)
        }
        centerCol.addView(rpmLabel)
        container.addView(centerCol)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, 110, 0.2f)
        }

        btnHold = Button(this).apply {
            text = "HOLD"
            textSize = 11f
            setOnClickListener {
                isHoldActive = !isHoldActive
                if (isHoldActive) heldRpmValue = currentRealRpm
                refreshAllUI()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                setMargins(4, 0, 0, 0)
            }
        }
        rightCol.addView(btnHold)
        container.addView(rightCol)

        return container
    }

    private fun buildSettingsTable(): View {
        val table = TableLayout(this).apply {
            setPadding(0, 4, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        btn2T = Button(this).apply { text = "2T"; setOnClickListener { saveConfig("engineType", 2); engineType = 2; refreshAllUI() } }
        btn4T = Button(this).apply { text = "4T"; setOnClickListener { saveConfig("engineType", 4); engineType = 4; refreshAllUI() } }
        btnElectro = Button(this).apply { text = "Электро"; setOnClickListener { saveConfig("engineType", 3); engineType = 3; refreshAllUI() } }

        btnLimit1 = Button(this).apply { text = "6k"; setOnClickListener { saveConfig("maxAllowedRpm", 6000); maxAllowedRpm = 6000; refreshAllUI() } }
        btnLimit2 = Button(this).apply { text = "12k"; setOnClickListener { saveConfig("maxAllowedRpm", 12000); maxAllowedRpm = 12000; refreshAllUI() } }
        btnLimit3 = Button(this).apply { text = "20k"; setOnClickListener { saveConfig("maxAllowedRpm", 20000); maxAllowedRpm = 20000; refreshAllUI() } }

        btnRateFast = Button(this).apply { text = "Fast"; setOnClickListener { saveConfig("audioBufferSize", 1536); audioBufferSize = 1536; refreshAllUI() } }
        btnRateNorm = Button(this).apply { text = "Norm"; setOnClickListener { saveConfig("audioBufferSize", 2560); audioBufferSize = 2560; refreshAllUI() } }
        btnRateSlow = Button(this).apply { text = "Slow"; setOnClickListener { saveConfig("audioBufferSize", 4096); audioBufferSize = 4096; refreshAllUI() } }

        btnSmoothSharp = Button(this).apply { text = "Sharp"; setOnClickListener { saveSmooth(0.02f, 0.05f) } }
        btnSmoothNorm = Button(this).apply { text = "Norm"; setOnClickListener { saveSmooth(0.06f, 0.18f) } }
        btnSmoothSoft = Button(this).apply { text = "Soft"; setOnClickListener { saveSmooth(0.15f, 0.40f) } }

        addRow(table, "мотор:", btn2T, btn4T, btnElectro)
        addRow(table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        return table
    }

    private fun addRow(table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
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

        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 0, 2, 0) }
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

    private fun saveConfig(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    private fun saveSmooth(rise: Float, drop: Float) {
        riseTimeConstant = rise
        dropTimeConstant = drop
        prefs.edit().putFloat("riseTimeConstant", rise).putFloat("dropTimeConstant", drop).apply()
        refreshAllUI()
    }

    private fun refreshAllUI() {
        if (!::btnHold.isInitialized) return

        btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)

        btn2T.setBackgroundColor(if (engineType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn2T.setTextColor(if (engineType == 2) Color.BLACK else Color.WHITE)
        btn4T.setBackgroundColor(if (engineType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btn4T.setTextColor(if (engineType == 4) Color.BLACK else Color.WHITE)
        btnElectro.setBackgroundColor(if (engineType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        btnElectro.setTextColor(if (engineType == 3) Color.BLACK else Color.WHITE)

        btnLimit1.setBackgroundColor(if (maxAllowedRpm == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit2.setBackgroundColor(if (maxAllowedRpm == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit3.setBackgroundColor(if (maxAllowedRpm == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        listOf(btnLimit1, btnLimit2, btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        btnRateFast.setBackgroundColor(if (audioBufferSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateNorm.setBackgroundColor(if (audioBufferSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateSlow.setBackgroundColor(if (audioBufferSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        listOf(btnRateFast, btnRateNorm, btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        val isSharp = (riseTimeConstant == 0.02f)
        val isNorm = (riseTimeConstant == 0.06f)
        btnSmoothSharp.setBackgroundColor(if (isSharp) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothNorm.setBackgroundColor(if (isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSoft.setBackgroundColor(if (!isSharp && !isNorm) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        listOf(btnSmoothSharp, btnSmoothNorm, btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioThread()
        }
    }

    private fun startAudioThread() {
        isRecording = true
        thread {
            val sampleRate = 8000
            val channel = AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            var recorder: AudioRecord? = null

            try {
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, format)
                if (minBuf <= 0) {
                    runOnUiThread { statusText.text = "Ошибка: Микрофон не поддерживается" }
                    return@thread
                }

                while (isRecording) {
                    val currentBufSize = maxOf(minBuf, audioBufferSize)
                    try {
                        recorder?.stop()
                        recorder?.release()
                    } catch (_: Exception) {}

                    recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, format, currentBufSize)
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        runOnUiThread { statusText.text = "Ошибка инициализации микрофона" }
                        Thread.sleep(1000)
                        continue
                    }

                    recorder.startRecording()
                    processStream(recorder, sampleRate, currentBufSize)
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun processStream(recorder: AudioRecord, sampleRate: Int, bufSize: Int) {
        val buffer = ShortArray(bufSize)
        var smoothedRpm = 0f
        val dt = bufSize.toFloat() / sampleRate.toFloat()

        while (isRecording && audioBufferSize == bufSize) {
            val readSize = recorder.read(buffer, 0, bufSize)
            if (readSize <= 0) continue

            var volume: Long = 0
            for (i in 0 until readSize) volume += abs(buffer[i].toLong())
            val avgVol = (volume / readSize).toInt()

            var rawRpm = 0
            var dominantFreq = 0f

            if (avgVol > volumeThreshold) {
                val minLag = sampleRate / 200
                val maxLag = sampleRate / 15
                var bestLag = -1
                var maxCorr: Long = 0

                var lag = minLag
                while (lag <= maxLag) {
                    var corr: Long = 0
                    val limit = readSize - lag
                    var i = 0
                    while (i < limit) {
                        corr += buffer[i].toLong() * buffer[i + lag].toLong()
                        i++
                    }
                    if (corr > maxCorr) {
                        maxCorr = corr
                        bestLag = lag
                    }
                    lag++
                }

                if (bestLag > 0) {
                    dominantFreq = sampleRate.toFloat() / bestLag
                    val calcRpm = when (engineType) {
                        4 -> (dominantFreq * 120).toInt()
                        else -> (dominantFreq * 60).toInt()
                    }
                    if (calcRpm in 500..maxAllowedRpm) rawRpm = calcRpm
                }
            }

            if (rawRpm > 0) {
                if (smoothedRpm == 0f) {
                    smoothedRpm = rawRpm.toFloat()
                } else {
                    val alpha = (1.0 - exp((-dt / riseTimeConstant).toDouble())).toFloat()
                    smoothedRpm += alpha * (rawRpm - smoothedRpm)
                }
            } else {
                val dropAlpha = (1.0 - exp((-dt / dropTimeConstant).toDouble())).toFloat()
                smoothedRpm *= (1f - dropAlpha)
                if (smoothedRpm < 300) smoothedRpm = 0f
            }

            val finalRpm = smoothedRpm.toInt()
            currentRealRpm = finalRpm
            val modeName = if (engineType == 4) "4T" else if (engineType == 3) "Электро" else "2T"

            runOnUiThread {
                debugText.text = "Громкость: $avgVol | Частота: ${dominantFreq.toInt()} Гц"
                if (isHoldActive) {
                    rpmText.text = (if (heldRpmValue > 0) heldRpmValue else 0).toString()
                    statusText.text = "Удержание (HOLD)"
                } else {
                    if (finalRpm > 0) {
                        rpmText.text = finalRpm.toString()
                        statusText.text = "Работает ($modeName)"
                    } else {
                        rpmText.text = "0"
                        statusText.text = if (avgVol > volumeThreshold) "Анализ тона..." else "Ожидание запуска мотора..."
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isRecording = false
        super.onDestroy()
    }
}
