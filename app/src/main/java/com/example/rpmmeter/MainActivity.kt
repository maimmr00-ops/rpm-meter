package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder.SettingsButtons
    
    private val volumeStepButtons = arrayOfNulls<Button>(10)
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

            // Инициализация таблицы настроек (старый визуал с кнопками и квадратами порога)
            uiBuilder = UIBuilder.buildSettingsTable(
                context = this,
                prefsManager = prefsManager,
                onRefreshUI = {
                    refreshAllUI()
                    restartAnalyzer()
                },
                volumeStepButtons = volumeStepButtons
            )

            rootLayout.addView(headerBuilder.topPanel)
            rootLayout.addView(headerBuilder.infoPanel)
            rootLayout.addView(uiBuilder.table)
            rootLayout.addView(headerBuilder.algorithmRow)

            refreshAllUI()

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
                    val progressVal = volume.coerceIn(0, headerBuilder.vuMeterBar.max)

                    // Если активен HOLD, замораживаем только отрисовку интерфейса оборотов
                    if (isHoldActive) return@AudioAnalyzer

                    runOnUiThread {
                        headerBuilder.tvRpmValue.text = rpm.toString()
                        headerBuilder.tvMainStatus.text = status
                        headerBuilder.tvDetails.text = detailsText
                        headerBuilder.tvFreqStatus.text = freqText
                        headerBuilder.vuMeterBar.progress = progressVal
                        updateVolumeSquaresUI(volume)
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

    private fun updateVolumeSquaresUI(currentVol: Int) {
        val currentSensitivityThreshold = prefsManager.minVolumeThreshold
        
        var thresholdIndex = 0
        for (i in 0 until 10) {
            if (UIBuilder.getThresholdForSquare(i) == currentSensitivityThreshold) {
                thresholdIndex = i
                break
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

    private fun refreshAllUI() {
        if (!::headerBuilder.isInitialized || !::uiBuilder.isInitialized) return

        headerBuilder.updateButtonStates()

        val eType = prefsManager.engineType
        uiBuilder.btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        uiBuilder.btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        uiBuilder.btnOthers.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btnOthers.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

        val limit = prefsManager.maxAllowedRpm
        uiBuilder.btnLimit1.setBackgroundColor(if (limit == 6000) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnLimit2.setBackgroundColor(if (limit == 12000) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnLimit3.setBackgroundColor(if (limit == 20000) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnLimit1, uiBuilder.btnLimit2, uiBuilder.btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        val bufSize = prefsManager.audioBufferSize
        uiBuilder.btnRateFast.setBackgroundColor(if (bufSize == 1536) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnRateNorm.setBackgroundColor(if (bufSize == 2560) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnRateSlow.setBackgroundColor(if (bufSize == 4096) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnRateFast, uiBuilder.btnRateNorm, uiBuilder.btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        val preset = prefsManager.smoothPreset
        uiBuilder.btnSmoothSharp.setBackgroundColor(if (preset == 0) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnSmoothNorm.setBackgroundColor(if (preset == 1) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        uiBuilder.btnSmoothSoft.setBackgroundColor(if (preset == 2) Color.parseColor("#3F51B5") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnSmoothSharp, uiBuilder.btnSmoothNorm, uiBuilder.btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }
        
        updateVolumeSquaresUI(0)
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
tCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAnalyzer()
            } else {
                Toast.makeText(this, "Требуется доступ к микрофону!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
