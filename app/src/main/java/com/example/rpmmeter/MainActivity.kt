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
    private lateinit var debugText: TextView
    private lateinit var btn2T: Button
    private lateinit var btn4T: Button

    private var isRecording = false
    private var engineType = 2
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

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }

        btn2T = Button(this).apply {
            text = "Режим 2T"
            setOnClickListener { engineType = 2; updateButtonsStyle() }
        }

        btn4T = Button(this).apply {
            text = "Режим 4T"
            setOnClickListener { engineType = 4; updateButtonsStyle() }
        }

        switchLayout.addView(btn2T)
        switchLayout.addView(btn4T)
        layout.addView(switchLayout)

        rpmText = TextView(this).apply {
            text = "0 RPM"
            textSize = 48f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        layout.addView(rpmText)

        statusText = TextView(this).apply {
            text = "Слушаем..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(statusText)

        // Отладочное поле для видения реальных цифр с микрофона
        debugText = TextView(this).apply {
            text = "Громкость: 0 | Частота: 0 Гц"
            textSize = 14f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        layout.addView(debugText)

        setContentView(layout)
        updateButtonsStyle()

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
            btn2T.setBackgroundColor(Color.parseColor("#00E676")); btn2T.setTextColor(Color.BLACK)
            btn4T.setBackgroundColor(Color.parseColor("#424242")); btn4T.setTextColor(Color.WHITE)
        } else {
            btn4T.setBackgroundColor(Color.parseColor("#00E676")); btn4T.setTextColor(Color.BLACK)
            btn2T.setBackgroundColor(Color.parseColor("#424242")); btn2T.setTextColor(Color.WHITE)
        }
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
                        var volume = 0L
                        for (i in 0 until readSize) {
                            volume += kotlin.math.abs(buffer[i].toLong())
                        }
                        val avgVolume = (volume / readSize).toInt()

                        // Считаем переходы через ноль без жестких фильтров
                        var crossings = 0
                        for (i in 0 until readSize - 1) {
                            if ((buffer[i] >= 0 && buffer[i + 1] < 0) || (buffer[i] < 0 && buffer[i + 1] >= 0)) {
                                crossings++
                            }
                        }

                        val durationSeconds = readSize.toFloat() / sampleRate
                        val frequency = (crossings / 2.0f) / durationSeconds

                        val rpm = if (engineType == 2) {
                            (frequency * 60).toInt()
                        } else {
                            (frequency * 120).toInt()
                        }

                        runOnUiThread {
                            debugText.text = "Громкость: $avgVolume | Част: ${frequency.toInt()} Гц"
                            if (avgVolume > 10) { // Снизили порог до минимума
                                rpmText.text = "$rpm RPM"
                                statusText.text = "Работает (${engineType}T)"
                            } else {
                                rpmText.text = "0 RPM"
                                statusText.text = "Тишина..."
                            }
                        }
                    }
                    Thread.sleep(40)
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
