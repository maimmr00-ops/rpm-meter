package com.example.rpmmeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder.SettingsButtons
    
    private val volumeStepButtons = arrayOfNulls<Button>(10)
    private var currentMultiplier = 1
    private var isHoldActive = false
    private var heldRpmValue = 0
    private var currentDisplayRpm = 0f
    private val PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // 01: Перехватчик фатальных ошибок (сохраняет стек в crash_log.txt для самопроверки)
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val logFile = File(getExternalFilesDir(null), "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            oldHandler?.uncaughtException(thread, throwable)
        }

        // 02: Инициализация менеджера настроек
        prefsManager = PreferencesManager(this)

        // 03: Корневой контейнер с растяжением на весь экран
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(12, 8, 12, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 04: Инициализация хелпера шапки (HeaderBuilder)
        headerBuilder = HeaderBuilder(
            context = this,
            onExit = { finish() },
            onHoldToggle = {
                isHoldActive = !isHoldActive
                if (isHoldActive) {
                    heldRpmValue = currentDisplayRpm.roundToInt()
                }
                refreshAllUI()
            },
            onMultiplierSelect = { mult ->
                currentMultiplier = mult
                refreshAllUI()
            }
        )

        // 05: Инициализация таблицы настроек через UIBuilder
        uiBuilder = UIBuilder.buildSettingsTable(
            context = this,
            prefsManager = prefsManager,
            onRefreshUI = { refreshAllUI() },
            volumeStepButtons = volumeStepButtons
        )

        // Сборка интерфейса экрана
        rootLayout.addView(headerBuilder.buildTopPanel())
        rootLayout.addView(headerBuilder.buildInfoPanelWithSides())
        rootLayout.addView(uiBuilder.table)

        // 06: Информационный копирайт внизу
        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.8"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 4)
        }
        rootLayout.addView(copyright)

        setContentView(rootLayout)

        refreshAllUI()

        // 07: Проверка разрешений на запись аудио
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_CODE)
        } else {
            initAndStartAudioAnalyzer()
        }
    }

    // 08: Перезапуск аудиоанализатора
    fun restartAnalyzer() {
        audioAnalyzer?.stop()
        audioAnalyzer = null
        initAndStartAudioAnalyzer()
    }

    // 09: Инициализация и запуск потока анализатора звука
    private fun initAndStartAudioAnalyzer() {
        audioAnalyzer?.stop()
        
        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            onUpdate = { rawRpm, allFreq, preFreq, vol, status ->
                val targetRpm = if (currentMultiplier > 0) (rawRpm / currentMultiplier) else rawRpm

                // Применение пресетов плавности тахометра
                val preset = prefsManager.smoothPreset
                val smoothedRpm = when (preset) {
                    0 -> targetRpm
                    1 -> currentDisplayRpm + (targetRpm - currentDisplayRpm) / 3.0f
                    else -> currentDisplayRpm + (targetRpm - currentDisplayRpm) / 7.0f
                }
                currentDisplayRpm = smoothedRpm

                val currentLiveRpm = currentDisplayRpm.roundToInt()
                val displayInt = if (isHoldActive) heldRpmValue else currentLiveRpm

                runOnUiThread {
                    val currentThreshold = prefsManager.minVolumeThreshold

                    if (isHoldActive) {
                        headerBuilder.statusLine1.text = "HOLD. Живая частота: $currentLiveRpm rpm"
                        headerBuilder.statusLine1.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        if (vol < currentThreshold) {
                            headerBuilder.statusLine1.text = "Ожидание запуска двигателя"
                            headerBuilder.statusLine1.setTextColor(Color.YELLOW)
                        } else {
                            headerBuilder.statusLine1.text = status
                            headerBuilder.statusLine1.setTextColor(Color.parseColor("#00E676"))
                        }
                    }

                    headerBuilder.statusLine2.text = "Громкость: $vol | Порог: $currentThreshold"
                    headerBuilder.statusLine3.text = "All: ${allFreq.roundToInt()} Гц | Pre-Freq: ${preFreq.roundToInt()} Гц"

                    updateRpmDisplay(displayInt)
                    updateVolumeSquaresUI(vol)
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    headerBuilder.statusLine1.text = errorMsg
                    headerBuilder.statusLine1.setTextColor(Color.RED)
                }
            }
        )
        audioAnalyzer?.start()
    }

    // 10: Форматирование и вывод цифр на главный экран
    private fun updateRpmDisplay(value: Int) {
        val clamped = value.coerceIn(0, 99999)
        val formatted = String.format("%5d", clamped).replace(' ', '\u00A0')
        headerBuilder.rpmTextView.text = formatted
    }

    // 11: Обновление VU-метра (индикатор громкости из 10 квадратов)
    private fun updateVolumeSquaresUI(currentVol: Int) {
        val currentSensitivityThreshold = prefsManager.minVolumeThreshold
        var thresholdIndex = 0
        if (prefsManager.hasStoredThreshold()) {
            for (i in 0 until 10) {
                if (UIBuilder.getThresholdForSquare(i) == currentSensitivityThreshold) {
                    thresholdIndex = i
                    break
                }
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
                i == thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#00E676"))
                i == thresholdIndex -> btn.setBackgroundColor(Color.parseColor("#FF9800"))
                i < thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#00BCD4"))
                i > thresholdIndex && volumeIndex >= i -> btn.setBackgroundColor(Color.parseColor("#D0F8E8"))
                else -> btn.setBackgroundColor(Color.parseColor("#37474F"))
            }
        }
    }

    // 12: Синхронизация цветов и состояний всех элементов управления интерфейса
    private fun refreshAllUI() {
        if (!::headerBuilder.isInitialized || !::uiBuilder.isInitialized) return

        headerBuilder.btnHold.setBackgroundColor(if (isHoldActive) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
        headerBuilder.btnHold.setTextColor(if (isHoldActive) Color.BLACK else Color.WHITE)
        headerBuilder.btnExit.setBackgroundColor(Color.parseColor("#424242"))
        headerBuilder.btnExit.setTextColor(Color.WHITE)

        headerBuilder.btnX1.setBackgroundColor(if (currentMultiplier == 1) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX1.setTextColor(if (currentMultiplier == 1) Color.BLACK else Color.WHITE)
        headerBuilder.btnX2.setBackgroundColor(if (currentMultiplier == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX2.setTextColor(if (currentMultiplier == 2) Color.BLACK else Color.WHITE)
        headerBuilder.btnX3.setBackgroundColor(if (currentMultiplier == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX3.setTextColor(if (currentMultiplier == 3) Color.BLACK else Color.WHITE)
        headerBuilder.btnX4.setBackgroundColor(if (currentMultiplier == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        headerBuilder.btnX4.setTextColor(if (currentMultiplier == 4) Color.BLACK else Color.WHITE)

        val eType = prefsManager.engineType
        uiBuilder.btn2T.setBackgroundColor(if (eType == 2) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn2T.setTextColor(if (eType == 2) Color.BLACK else Color.WHITE)
        uiBuilder.btn4T.setBackgroundColor(if (eType == 4) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btn4T.setTextColor(if (eType == 4) Color.BLACK else Color.WHITE)
        uiBuilder.btnOthers.setBackgroundColor(if (eType == 3) Color.parseColor("#00E676") else Color.parseColor("#424242"))
        uiBuilder.btnOthers.setTextColor(if (eType == 3) Color.BLACK else Color.WHITE)

        val limit = prefsManager.maxAllowedRpm
        uiBuilder.btnLimit1.setBackgroundColor(if (limit == 6000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        uiBuilder.btnLimit2.setBackgroundColor(if (limit == 12000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        uiBuilder.btnLimit3.setBackgroundColor(if (limit == 20000) Color.parseColor("#0288D1") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnLimit1, uiBuilder.btnLimit2, uiBuilder.btnLimit3).forEach { it.setTextColor(Color.WHITE) }

        val bufSize = prefsManager.audioBufferSize
        uiBuilder.btnRateFast.setBackgroundColor(if (bufSize == 1536) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        uiBuilder.btnRateNorm.setBackgroundColor(if (bufSize == 2560) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        uiBuilder.btnRateSlow.setBackgroundColor(if (bufSize == 4096) Color.parseColor("#E91E63") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnRateFast, uiBuilder.btnRateNorm, uiBuilder.btnRateSlow).forEach { it.setTextColor(Color.WHITE) }

        val preset = prefsManager.smoothPreset
        uiBuilder.btnSmoothSharp.setBackgroundColor(if (preset == 0) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        uiBuilder.btnSmoothNorm.setBackgroundColor(if (preset == 1) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        uiBuilder.btnSmoothSoft.setBackgroundColor(if (preset == 2) Color.parseColor("#AB47BC") else Color.parseColor("#424242"))
        listOf(uiBuilder.btnSmoothSharp, uiBuilder.btnSmoothNorm, uiBuilder.btnSmoothSoft).forEach { it.setTextColor(Color.WHITE) }

        // 13: Вызов инкапсулированной логики алгоритмов из UIBuilder
        UIBuilder.updateAlgorithmButtons(
            prefsManager,
            uiBuilder.btnAlg1,
            uiBuilder.btnAlg2,
            uiBuilder.btnAlg3,
            uiBuilder.btnAlg4
        )
        
        updateVolumeSquaresUI(0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initAndStartAudioAnalyzer()
        }
    }

    override fun onDestroy() {
        audioAnalyzer?.stop()
        super.onDestroy()
    }
}
