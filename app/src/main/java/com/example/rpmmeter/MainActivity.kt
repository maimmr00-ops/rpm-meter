package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder
    
    private var isRunning = false
    var isHoldActive = false // Флаг для кнопки HOLD

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Перехватчик фатальных ошибок на случай непредвиденных сбоев
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                
                val logFile = java.io.File(getExternalFilesDir(null), "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            oldHandler?.uncaughtException(thread, throwable)
        }

        try {
            prefsManager = PreferencesManager(this)

            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#121212"))
                setPadding(8, 8, 8, 8)
            }
            setContentView(rootLayout)

            // Инициализация шапки (кнопки EXIT/HOLD, VU-метр, детальные строки)
            headerBuilder = HeaderBuilder(this, prefsManager) { selectedAlgIndex ->
                prefsManager.algorithmIndex = selectedAlgIndex
                restartAnalyzer()
            }

            // Инициализация таблицы настроек
            uiBuilder = UIBuilder(this, prefsManager) {
                headerBuilder.updateButtonStates()
                restartAnalyzer()
            }

            rootLayout.addView(headerBuilder.topPanel)
            rootLayout.addView(headerBuilder.infoPanel)
            rootLayout.addView(uiBuilder.table)
            rootLayout.addView(headerBuilder.algorithmRow)

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка UI: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAudioPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        stopAnalyzer()
    }

    private fun checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startAnalyzer()
        }
    }

    private fun startAnalyzer() {
        if (isRunning) return
        isRunning = true

        try {
            audioAnalyzer = AudioAnalyzer(
                prefsManager = prefsManager,
                selectedAlgorithmIndex = prefsManager.algorithmIndex,
                onUpdate = { rpm, freq, volume, status ->
                    val minThresh = prefsManager.minVolumeThreshold
                    val detailsText = "Громкость: $volume | Порог: $minThresh"
                    val freqText = "Частота: ${freq.toInt()} Гц | Статус: ${if (volume >= minThresh) "Активно" else "Ниже порога"}"
                    val progressVal = volume.coerceIn(0, 1000)

                    // Если активен HOLD, замораживаем только отрисовку интерфейса
                    if (isHoldActive) return@AudioAnalyzer

                    runOnUiThread {
                        headerBuilder.tvRpmValue.text = rpm.toString()
                        headerBuilder.tvMainStatus.text = status
                        headerBuilder.tvDetails.text = detailsText
                        headerBuilder.tvFreqStatus.text = freqText
                        headerBuilder.vuMeterBar.progress = progressVal
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        headerBuilder.tvMainStatus.text = "Ошибка: $err"
                    }
                }
            )
            audioAnalyzer?.start()
        } catch (e: Exception) {
            isRunning = false
        }
    }

    private fun stopAnalyzer() {
        if (!isRunning) return
        isRunning = false
        try {
            audioAnalyzer?.stop()
        } catch (_: Exception) {}
        audioAnalyzer = null
    }

    fun restartAnalyzer() {
        stopAnalyzer()
        startAnalyzer()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAnalyzer()
            } else {
                Toast.makeText(this, "Требуется доступ к микрофону!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
