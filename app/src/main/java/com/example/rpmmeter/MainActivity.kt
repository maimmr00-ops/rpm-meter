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
    private lateinit var btnSpeedLow: Button
    private lateinit var btnSpeedMed: Button
    private lateinit var btnSpeedHard: Button

    private var isRecording = false
    private var engineType = 2
    private var maxAllowedRpm = 12000
    private var volumeThreshold = 100

    // Коэффициенты сглаживания (скорость обновления / инерция)
    // Low (медленно/плавно), Medium (средне), Hard (мгновенно/резко)
    private var smoothingFactor = 0.6f 
    private var dropFactor = 0.3f

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(30, 30, 30, 30)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Режим двигателя (2T / 4T)
        val engineBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 10)
        }

        btn2T = Button(this).apply {
            text = "Режим 2T"
            setOnClickListener { engineType = 2; updateEngineButtons() }
        }
        btn4T = Button(this).apply {
            text = "Режим 4T"
            setOnClickListener { engineType = 4; updateEngineButtons() }
        }
        engineBar.addView(btn2T)
        engineBar.addView(btn4T)
        layout.addView(engineBar)

        // 2. Лимит оборотов
        val limitBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        btnLimit1 = Button(this).apply {
            text = "До 6k"
            setOnClickListener { maxAllowedRpm = 6000; updateLimitButtons() }
        }
        btnLimit2 = Button(this).apply {
            text = "До 12k"
            setOnClickListener { maxAllowedRpm = 12000; updateLimitButtons() }
        }
        btnLimit3 = Button(this).apply {
            text = "До 20k"
            setOnClickListener { maxAllowedRpm = 20000; updateLimitButtons() }
        }
        limitBar.addView(btnLimit1)
        limitBar.addView(btnLimit2)
        limitBar.addView(btnLimit3)
        layout.addView(limitBar)

        // 3. Скорость обновления (Лов / Медиум / Хард)
        val speedBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        btnSpeedLow = Button(this).apply {
            text = "Low (Плавный)"
            setOnClickListener { 
                smoothingFactor = 0.2f
                dropFactor = 0.8f
                updateSpeedButtons() 
            }
        }
        btnSpeedMed = Button(this).apply {
            text = "Med"
            setOnClickListener { 
                smoothingFactor = 0.6f
                dropFactor = 0.3f
                updateSpeedButtons() 
            }
        }
        btnSpeedHard = Button(this).apply {
            text = "Hard (Резкий)"
            setOnClickListener { 
                smoothingFactor = 0.95f
                dropFactor = 0.05f
                updateSpeedButtons() 
            }
        }
        speedBar.addView(btnSpeedLow)
        speedBar.addView(btnSpeedMed)
        speedBar.addView(btnSpeedHard)
        layout.addView(speedBar)

        // Крупные цифры оборотов
        rpmText = TextView(this).apply {
            text = "0 000"
            textSize = 68f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }
        layout.addView(rpmText)

        val labelRpmText = TextView(this).apply {
            text = "RPM"
            textSize = 18f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(labelRpmText)

        // Статус
        statusText = TextView(this).apply {
            text = "Ожидание запуска мотора..."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        // Отладка
        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 13f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 0)
        }
        layout.addView(debugText)

        setContentView(layout)
        updateEngineButtons()
        updateLimitButtons()
        updateSpeedButtons()

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
        if (engineType == 2) {
            btn2T.setBackgroundColor(Color.parseColor("#00E676")); btn2T.setTextColor(Color.BLACK)
            btn4T.setBackgroundColor(Color.parseColor("#424242")); btn4T.setTextColor(Color.WHITE)
        } else {
            btn4T.setBackgroundColor(Color.parseColor("#00E676")); btn4T.setTextColor(Color.BLACK)
            btn2T.setBackgroundColor(Color.parseColor("#424242")); btn2T.setTextColor(Color.WHITE)
        }
    }

    private fun updateLimitButtons() {
        btnLimit1.setBackgroundColor(if (maxAllowedRpm == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit2.setBackgroundColor(if (maxAllowedRpm == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit3.setBackgroundColor(if (maxAllowedRpm == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        btnLimit1.setTextColor(Color.WHITE)
        btnLimit2.setTextColor(Color.WHITE)
        btnLimit3.setTextColor(Color.WHITE)
    }

    private fun updateSpeedButtons() {
        btnSpeedLow.setBackgroundColor(if (smoothingFactor == 0.2f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSpeedMed.setBackgroundColor(if (smoothingFactor == 0.6f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSpeedHard.setBackgroundColor(if (smoothingFactor == 0.95f) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        btnSpeedLow.setTextColor(Color.WHITE)
        btnSpeedMed.setTextColor(Color.WHITE)
        btnSpeedHard.setTextColor(Color.WHITE)
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
            val bufferSize = 2048

            try {
                // Используем VOICE_COMMUNICATION или UNPROCESSED (если поддерживается), 
                // чтобы отключить встроенное АРУ (автоматическую регулировку усиления телефона), 
                // из-за которой микрофон «запирает» и плавает по громкости.
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(bufferSize)
                )

                val buffer = ShortArray(bufferSize)
                audioRecord.startRecording()
                var smoothedRpm = 0f

                while (isRecording) {
                    val readSize = audioRecord.read(buffer, 0, bufferSize)
                    if (readSize > 0) {
                        var volume = 0L
                        for (i in 0 until readSize) {
                            volume += abs(buffer[i].toLong())
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
                                    correlation += (buffer[i].toLong() * buffer[i + lag].toLong())
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
