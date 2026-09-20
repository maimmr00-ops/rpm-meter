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

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var rpmText: TextView
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button

    private var isRecording = false
    private var engineType = 2 // 2 — это 2T, 4 — это 4T
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Главный контейнер
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Панель с кнопками выбора 2T / 4T
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }

        btn2T = Button(this).apply {
            text = "Режим 2T"
            textSize = 16f
            setOnClickListener {
                engineType = 2
                updateButtonsStyle()
            }
        }

        btn4T = Button(this).apply {
            text = "Режим 4T"
            textSize = 16f
            setOnClickListener {
                engineType = 4
                updateButtonsStyle()
            }
        }

        switchLayout.addView(btn2T)
        switchLayout.addView(btn4T)
        layout.addView(switchLayout)

        // Крупный вывод RPM
        rpmText = TextView(this).apply {
            text = "0 RPM"
            textSize = 52f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        layout.addView(rpmText)

        // Статусная строка
        statusText = TextView(this).apply {
            text = "Инициализация..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        setContentView(layout)
        updateButtonsStyle()

        // Проверка разрешений на микрофон
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

    private fun updateButtonsStyle() {
        if (engineType == 2) {
            btn2T.setBackgroundColor(Color.parseColor("#00E676"))
            btn2T.setTextColor(Color.BLACK)
            btn4T.setBackgroundColor(Color.parseColor("#424242"))
            btn4T.setTextColor(Color.WHITE)
        } else {
            btn4T.setBackgroundColor(Color.parseColor("#00E676"))
            btn4T.setTextColor(Color.BLACK)
            btn2T.setBackgroundColor(Color.parseColor("#424242"))
            btn2T.setTextColor(Color.WHITE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAudioThread()
        } else {
            statusText.text = "Нет доступа к микрофону!"
        }
    }

    private fun startAudioThread() {
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
                        // Вычисляем общую громкость (амплитуду) для фильтрации тишины
                        var volume = 0L
                        for (i in 0 until readSize) {
                            volume += kotlin.math.abs(buffer[i].toLong())
                        }
                        val avgVolume = volume / readSize

                        var rpm = 0
                        if (avgVolume > 100) { // Порог шума, чтобы не реагировать на тишину
                            // Считаем частоту через переходы через ноль (Zero-Crossing Rate)
                            var crossings = 0
                            for (i in 0 until readSize - 1) {
                                if ((buffer[i] >= 0 && buffer[i + 1] < 0) || (buffer[i] < 0 && buffer[i + 1] >= 0)) {
                                    crossings++
                                }
                            }

                            // Частота сигнала = (количество пересечений / 2) * (sampleRate / размер буфера)
                            val durationSeconds = readSize.toFloat() / sampleRate
                            val frequency = (crossings / 2.0f) / durationSeconds

                            // Перевод в RPM с учетом типа двигателя (2T или 4T)
                            rpm = if (engineType == 2) {
                                (frequency * 60).toInt()
                            } else {
                                (frequency * 120).toInt() // 4T делает вспышку в 2 раза реже
                            }
                            
                            // Фильтрация нереалистичных значений
                            if (rpm < 500 || rpm > 15000) {
                                rpm = 0
                            }
                        }

                        runOnUiThread {
                            if (rpm > 0) {
                                rpmText.text = "$rpm RPM"
                                statusText.text = "Режим: ${engineType}T | Громкость: $avgVolume"
                            } else {
                                rpmText.text = "0 RPM"
                                statusText.text = if (avgVolume > 100) "Сигнал нестабилен..." else "Ожидание звука двигателя..."
                            }
                        }
                    }
                    Thread.sleep(60)
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

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
    }
}
