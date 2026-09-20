package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : BundleActivityCompatibility() { // Либо AppCompatActivity, если у вас стандартно

}

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var audioAnalyzer: AudioAnalyzer

    private lateinit var tvRpm: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnExit: Button
    private lateinit var btnHold: Button

    // Кнопки типа мотора
    private lateinit var btnEngine2T: Button
    private lateinit var btnEngine4T: Button
    private lateinit var btnEngineOthers: Button

    // Кнопки лимита
    private lateinit var btnLimit6k: Button
    private lateinit var btnLimit12k: Button
    private lateinit var btnLimit20k: Button

    // Кнопки обновления
    private lateinit var btnFast: Button
    private lateinit var btnNormUpdate: Button
    private lateinit var btnSlow: Button

    // Кнопки плавности
    private lateinit var btnSharp: Button
    private lateinit var btnNormSmooth: Button
    private lateinit var btnSoft: Button

    // Индикатор громкости (10 квадратиков)
    private lateinit var volumeBlocks: Array<View>

    private var isHoldActive = false
    private var lastValidRpm = 0
    private var lastValidFreq = 0f
    private var lastValidVol = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAnalyzer()
        } else {
            Toast.makeText(this, "Требуется разрешение на запись аудио!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PreferencesManager(this)

        initViews()
        setupListeners()
        updateUIState()

        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            onUpdate = { rpm, freq, vol, status ->
                runOnUiThread {
                    if (!isHoldActive) {
                        lastValidRpm = rpm
                        lastValidFreq = freq
                        lastValidVol = vol

                        tvRpm.text = rpm.toString()
                        tvStatus.text = status
                        tvDetails.text = "Громкость: $vol | Частота: ${freq.roundToInt()} Гц"
                        updateVolumeUI(vol)
                    }
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        checkAudioPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        audioAnalyzer.stop()
    }

    private fun checkAudioPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startAnalyzer()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startAnalyzer() {
        audioAnalyzer.start()
    }

    private fun initViews() {
        tvRpm = findViewById(R.id.tvRpm)
        tvStatus = findViewById(R.id.tvStatus)
        tvDetails = findViewById(R.id.tvDetails)
        btnExit = findViewById(R.id.btnExit)
        btnHold = findViewById(R.id.btnHold)

        btnEngine2T = findViewById(R.id.btnEngine2T)
        btnEngine4T = findViewById(R.id.btnEngine4T)
        btnEngineOthers = findViewById(R.id.btnEngineOthers)

        btnLimit6k = findViewById(R.id.btnLimit6k)
        btnLimit12k = findViewById(R.id.btnLimit12k)
        btnLimit20k = findViewById(R.id.btnLimit20k)

        btnFast = findViewById(R.id.btnFast)
        btnNormUpdate = findViewById(R.id.btnNormUpdate)
        btnSlow = findViewById(R.id.btnSlow)

        btnSharp = findViewById(R.id.btnSharp)
        btnNormSmooth = findViewById(R.id.btnNormSmooth)
        btnSoft = findViewById(R.id.btnSoft)

        // Инициализация 10 блоков шкалы громкости (убедитесь, что ID в activity_main.xml называются block0...block9)
        volumeBlocks = arrayOf(
            findViewById(R.id.block0),
            findViewById(R.id.block1),
            findViewById(R.id.block2),
            findViewById(R.id.block3),
            findViewById(R.id.block4),
            findViewById(R.id.block5),
            findViewById(R.id.block6),
            findViewById(R.id.block7),
            findViewById(R.id.block8),
            findViewById(R.id.block9)
        )
    }

    private fun setupListeners() {
        btnExit.setfinishOnClickListener { finish() } // или standart finish()

        btnHold.setOnClickListener {
            isHoldActive = !isHoldActive
            if (isHoldActive) {
                btnHold.text = "RES"
                btnHold.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            } else {
                btnHold.text = "HOLD"
                btnHold.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

        // Выбор типа мотора
        btnEngine2T.setOnClickListener {
            prefsManager.engineType = 2
            updateUIState()
        }
        btnEngine4T.setOnClickListener {
            prefsManager.engineType = 4
            updateUIState()
        }
        btnEngineOthers.setOnClickListener {
            prefsManager.engineType = 0
            updateUIState()
        }

        // Лимиты оборотов
        btnLimit6k.setOnClickListener {
            prefsManager.maxAllowedRpm = 6000
            updateUIState()
        }
        btnLimit12k.setOnClickListener {
            prefsManager.maxAllowedRpm = 12000
            updateUIState()
        }
        btnLimit20k.setOnClickListener {
            prefsManager.maxAllowedRpm = 20000
            updateUIState()
        }

        // Буфер / Обновление
        btnFast.setOnClickListener {
            prefsManager.audioBufferSize = 1280
            updateUIState()
        }
        btnNormUpdate.setOnClickListener {
            prefsManager.audioBufferSize = 2560
            updateUIState()
        }
        btnSlow.setOnClickListener {
            prefsManager.audioBufferSize = 5120
            updateUIState()
        }

        // Плавность (скорость нарастания/падения)
        btnSharp.setOnClickListener {
            prefsManager.saveSmooth(0.25f, 0.4f)
            updateUIState()
        }
        btnNormSmooth.setOnClickListener {
            prefsManager.saveSmooth(0.06f, 0.18f)
            updateUIState()
        }
        btnSoft.setOnClickListener {
            prefsManager.saveSmooth(0.02f, 0.08f)
            updateUIState()
        }

        // Клик по блокам шкалы громкости для интерактивной настройки порога
        for ((index, block) in volumeBlocks.withIndex()) {
            block.setOnClickListener {
                val threshold = getThresholdForSquare(index)
                prefsManager.minVolumeThreshold = threshold
                updateVolumeUI(threshold) // подсветим выбранный уровень
                Toast.makeText(this, "Порог громкости изменен: $threshold", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUIState() {
        // Подсветка кнопок мотора
        val activeColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.darker_gray)

        btnEngine2T.setBackgroundColor(if (prefsManager.engineType == 2) activeColor else inactiveColor)
        btnEngine4T.setBackgroundColor(if (prefsManager.engineType == 4) activeColor else inactiveColor)
        btnEngineOthers.setBackgroundColor(if (prefsManager.engineType == 0) activeColor else inactiveColor)

        // Подсветка лимитов
        val limitColor = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        btnLimit6k.setBackgroundColor(if (prefsManager.maxAllowedRpm == 6000) limitColor else inactiveColor)
        btnLimit12k.setBackgroundColor(if (prefsManager.maxAllowedRpm == 12000) limitColor else inactiveColor)
        btnLimit20k.setBackgroundColor(if (prefsManager.maxAllowedRpm == 20000) limitColor else inactiveColor)

        // Подсветка обновления
        val updateColor = ContextCompat.getColor(this, android.R.color.holo_red_light)
        btnFast.setBackgroundColor(if (prefsManager.audioBufferSize == 1280) updateColor else inactiveColor)
        btnNormUpdate.setBackgroundColor(if (prefsManager.audioBufferSize == 2560) updateColor else inactiveColor)
        btnSlow.setBackgroundColor(if (prefsManager.audioBufferSize == 5120) updateColor else inactiveColor)

        // Подсветка плавности
        val smoothColor = ContextCompat.getColor(this, android.R.color.holo_purple)
        val rise = prefsManager.riseTimeConstant
        btnSharp.setBackgroundColor(if (rise == 0.25f) smoothColor else inactiveColor)
        btnNormSmooth.setBackgroundColor(if (rise == 0.06f) smoothColor else inactiveColor)
        btnSoft.setBackgroundColor(if (rise == 0.02f) smoothColor else inactiveColor)
    }

    // Обработка кнопки Exit, если вместо setfinishOnClickListener использовался обычный клик
    private fun Button.setfinishOnClickListener(listener: (View) -> Unit) {
        this.setOnClickListener(listener)
    }

    private fun getThresholdForSquare(index: Int): Int {
        return when (index) {
            0 -> 8000  // 1-й квадрат: самый жесткий порог (глушит всё тише 8000)
            1 -> 7000
            2 -> 6000
            3 -> 5000
            4 -> 4000
            5 -> 3000
            6 -> 2000
            7 -> 1500
            8 -> 800
            else -> 300 // 10-й квадрат: минимальный порог, пропускает почти всё
        }
    }

    private fun updateVolumeUI(currentVolume: Int) {
        val currentThreshold = prefsManager.minVolumeThreshold
        
        // Подсвечиваем кубики в зависимости от текущей громкости микрофона
        for (i in volumeBlocks.indices) {
            val blockThreshold = getThresholdForSquare(i)
            // Если текущая громкость выше или равна порогу этого квадрата — подсвечиваем зеленым/активным
            if (currentVolume >= blockThreshold || (currentVolume >= currentThreshold && i == 0)) {
                volumeBlocks[i].setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            } else {
                volumeBlocks[i].setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }
}
