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
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button

    private var isRecording = false
    private var engineType = 2 // По умолчанию 2Т (2-тактный)
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Переключатели режимов 2Т / 4Т
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        btn2T = Button(this).apply {
            text = "Режим 2T"
            setOnClickListener {
                engineType = 2
                updateButtonsStyle()
            }
        }

        btn4T = Button(this).apply {
            text = "Режим 4T"
            setOnClickListener {
                engineType = 4
                updateButtonsStyle()
            }
        }

        switchLayout.addView(btn2T)
        switchLayout.addView(btn4T)
        layout.addView(switchLayout)

        // Большое поле для вывода RPM
        rpmText = TextView(this).apply {
            text = "0 RPM"
            textSize = 48f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 50)
        }
        layout.addView(rpmText)

        // Информационный статус
        statusText = TextView(this).apply {
            text = "Инициализация микрофона..."
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        setContentView(layout)
        updateButtonsStyle()

        // Запрос разрешений
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startRpmCalculation()
        }
    }

    private fun updateButtonsStyle() {
        if (engineType == 2) {
            btn2T.setBackgroundColor(Color.DKGRAY)
            btn2T.setTextColor(Color.GREEN)
            btn4T.setBackgroundColor(Color.LTGRAY)
            btn4T.setTextColor(Color.BLACK)
        } else {
            btn4T.setBackgroundColor(Color.DKGRAY)
            btn4T.setTextColor(Color.GREEN)
            btn2T.setBackgroundColor(Color.LTGRAY)
            btn2T.setTextColor(Color.BLACK)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRpmCalculation()
        } else {
            statusText.text = "Нет доступа к микрофону!"
        }
    }

    private fun startRpmCalculation() {
        isRecording = true
        statusText.text = "Слушаем двигатель..."

        thread {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

            try {
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                val buffer = ShortArray(bufferSize)
                audioRecord.startRecording()

                while (isRecording) {
                    val readSize = audioRecord.read(buffer, 0, bufferSize)
                    if (readSize > 0) {
                        // Простой поиск пиков (частоты выхлопа/оборотов) методом автокорреляции во временной области
                        val freq = estimateFrequency(buffer, readSize, sampleRate)
                        
                        // Пересчет частоты в RPM в зависимости от типа двигателя (2T или 4T)
                        // Для 2T: 1 вспышка за 1 оборот -> RPM = freq * 60
                        // Для 4T: 1 вспышка за 2 оборота -> RPM = freq * 60 * 2
                        val calculatedRpm = if (freq > 15 && freq < 300) {
                            if (engineType == 2) (freq * 60).toInt() else (freq * 120).toInt()
                        } else {
                            0
                        }

                        runOnUiThread {
                            if (calculatedRpm > 0) {
                                rpmText.text = "$calculatedRpm RPM"
                                statusText.text = "Частота: ${freq.toInt()} Гц ($engineType-тактный)"
                            } else {
                                rpmText.text = "0 RPM"
                                statusText.text = "Ожидание стабильного сигнала..."
                            }
                        }
                    }
                    Thread.sleep(50)
                }
                audioRecord.stop()
                audioRecord.release()
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Ошибка: ${e.message}"
                }
            }
        }
    }

    // Метод оценки основной частоты звука (питча)
    private fun estimateFrequency(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        // Проверка порога громкости (шумоподавление)
        var sum = 0L
        for (i in 0 until size) sum += abs(buffer[i].toInt())
        val avg = sum / size
        if (avg < 50) return 0f // Тишина

        // Поиск периода с помощью автокорреляции
        var bestLag = -1
        var maxCorrelation = -1L

        val minLag = sampleRate / 300 // Макс частота 300 Гц (~18000 RPM для 2T)
        val maxLag = sampleRate / 15  // Мин частота 15 Гц

        for (lag in minLag..maxLag.coerceAtMost(size / 2)) {
            var correlation = 0L
            for (i in 0 until (size - lag)) {
                correlation += (buffer[i].toLong() * buffer[i + lag].toLong())
            }
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestLag = lag
            }
        }

        return if (bestLag > 0) sampleRate.toFloat() / bestLag else 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
    }
}
