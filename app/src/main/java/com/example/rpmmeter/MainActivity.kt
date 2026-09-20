package com.example.rpmmeter

import android.Manifest
import android.app.Activity
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
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button
    private lateinit var btnLimit1: Button
    private lateinit var btnLimit2: Button
    private lateinit var btnLimit3: Button
    
    // Кнопки обновления (размер буфера)
    private lateinit var btnRateFast: Button
    private lateinit var btnRateNorm: Button
    private lateinit var btnRateSlow: Button

    // Кнопки плавности / затухания
    private lateinit var btnSmoothSharp: Button
    private lateinit var btnSmoothNorm: Button
    private lateinit var btnSmoothSoft: Button

    private var isRecording = false
    private var engineType = 2
    private var maxAllowedRpm = 12000
    private var volumeThreshold = 30

    private var audioBufferSize = 1024 
    private var smoothingFactor = 0.8f 
    private var dropFactor = 0.2f

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- ВЕРХНЯЯ ЧАСТЬ: Обороты и статус ---

        // Крупные цифры оборотов
        rpmText = TextView(this).apply {
            text = "0 000"
            textSize = 68f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
        }
        layout.addView(rpmText)

        val labelRpmText = TextView(this).apply {
            text = "RPM"
            textSize = 16f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }
        layout.addView(labelRpmText)

        // Статус
        statusText = TextView(this).apply {
            text = "Ожидание запуска мотора..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        // Отладка
        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 12f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 16)
        }
        layout.addView(debugText)


        // --- НИЖНЯЯ ЧАСТЬ: Кнопки и настройки (Сетка) ---

        val tableLayout = TableLayout(this).apply {
            setPadding(0, 8, 0, 0)
        }

        // Вспомогательная функция для создания строк таблицы
        fun addSettingRow(labelTxt: String, b1: Button, b2: Button, b3: Button) {
            val row = TableRow(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val label = TextView(this).apply {
                text = labelTxt
                textSize = 13f
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

        // Инициализация кнопок
        btn2T = Button(this).apply { text = "2T"; setOnClickListener { engineType = 2; updateEngineButtons() } }
        btn4T = Button(this).apply { text = "4T"; setOnClickListener { engineType = 4; updateEngineButtons() } }
        
        btnLimit1 = Button(this).apply { text = "6k"; setOnClickListener { maxAllowedRpm = 6000; updateLimitButtons() } }
        btnLimit2 = Button(this).apply { text = "12k"; setOnClickListener { maxAllowedRpm = 12000; updateLimitButtons() } }
        btnLimit3 = Button(this).apply { text = "20k"; setOnClickListener { maxAllowedRpm = 20000; updateLimitButtons() } }

        btnRateFast = Button(this).apply { text = "Fast"; setOnClickListener { audioBufferSize = 1024; updateRateButtons() } }
        btnRateNorm = Button(this).apply { text = "Norm"; setOnClickListener { audioBufferSize = 2048; updateRateButtons() } }
        btnRateSlow = Button(this).apply { text = "Slow"; setOnClickListener { audioBufferSize = 4096; updateRateButtons() } }

        btnSmoothSharp = Button(this).apply { text = "Sharp"; setOnClickListener { smoothingFactor = 1.0f; dropFactor = 0.0f; updateSmoothButtons() } }
        btnSmoothNorm = Button(this).apply { text = "Norm"; setOnClickListener { smoothingFactor = 0.8f; dropFactor = 0.2f; updateSmoothButtons() } }
        btnSmoothSoft = Button(this).apply { text = "Soft"; setOnClickListener { smoothingFactor = 0.4f; dropFactor = 0.5f; updateSmoothButtons() } }

        // 1. Строка двигателя (2T / 4T)
        val engineRow = TableRow(this).apply { setPadding(0, 4, 0, 4) }
        val engineLabel = TextView(this).apply {
            text = "двигатель:"
            textSize = 13f
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

        // 2. Лимит оборотов
        addSettingRow("лимит:", btnLimit1, btnLimit2, btnLimit3)

        // 3. Скорость обновления
        addSettingRow("обновление:", btnRateFast, btnRateNorm, btnRateSlow)

        // 4. Плавность
        addSettingRow("плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)

        layout.addView(tableLayout)

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
        btnRateFast.setBackgroundColor(if (audioBufferSize == 1024) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateNorm.setBackgroundColor(if (audioBufferSize == 2048) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateSlow.setBackgroundColor(if (audioBufferSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        btnRateFast.setTextColor(Color.WHITE); btnRateNorm.setTextColor(Color.WHITE); btnRateSlow.setTextColor(Color.WHITE)
    }

    private fun updateSmoothButtons() {
        btnSmoothSharp.setBackgroundColor(if (smoothingFactor == 1.0f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothNorm.setBackgroundColor(if (smoothingFactor == 0.8f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSoft.setBackgroundColor(if (smoothingFactor == 0.4f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSmoothSharp.setTextColor(Color.WHITE); btnSmoothNorm.setTextColor(Color.WHITE); btnSmoothSoft.setTextColor(Color.WHITE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioThread()
        }
    }

    private fun startAudioThread() {
        isRecording = true
        thread {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            try {
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                while (isRecording) {
                    val currentBufferSz = audioBufferSize
                    val audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        minBuf.coerceAtLeast(currentBufferSz)
                    )

                    val actualBuffer = ShortArray(currentBufferSz)
                    audioRecord.startRecording()
                    var smoothedRpm = 0f

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
                                val minLag = sampleRate / 200
                                val maxLag = sampleRate / 15
                                
                                var bestLag = -1
                                var maxCorrelation = 0L

                                for (lag in minLag..maxLag) {
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
                                if (smoothedRpm == 0f) smoothedRpm = rawRpm.toFloat()
                                else smoothedRpm = smoothedRpm * (1f - smoothingFactor) + rawRpm * smoothingFactor
                            } else {
                                smoothedRpm = smoothedRpm * dropFactor
                                if (smoothedRpm < 300) smoothedRpm = 0f
                            }

                            val finalRpm = smoothedRpm.toInt()

                            runOnUiThread {
                                debugText.text = "Громкость: $avgVolume | Частота: ${dominantFreq.toInt()} Гц"
                                if (finalRpm > 0) {
                                    rpmText.text = String.format("%,d", finalRpm).replace(',', ' ')
                                    statusText.text = "Работает (${engineType}T)"
                                } else {
                                    rpmText.text = "0 000"
                                    statusText.text = if (avgVolume > volumeThreshold) "Анализ тона..." else "Ожидание запуска мотора..."
                                }
                            }
                        }
                    }
                    audioRecord.stop()
                    audioRecord.release()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Ошибка: ${e.message}" }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
    }
}
