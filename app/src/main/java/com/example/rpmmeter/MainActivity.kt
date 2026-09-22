package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var audioAnalyzer: AudioAnalyzer
    private lateinit var rootLayout: LinearLayout
    
    private lateinit var tvRpmValue: TextView
    private lateinit var tvStatusValue: TextView
    
    // Строители интерфейса
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder
    
    private var isRunning = false

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализируем менеджер настроек
        prefsManager = PreferencesManager(this)

        // 2. Главный корневой контейнер
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(12, 12, 12, 12)
        }
        setContentView(rootLayout)

        // 3. Собираем UI с помощью наших билдеров
        setupUI()

        // 4. Проверяем микрофон и запускаем анализ
        checkAudioPermissionAndStart()
    }

    private fun setupUI() {
        // Инициализируем шапку и блок алгоритмов
        headerBuilder = HeaderBuilder(this, prefsManager) { selectedAlgIndex ->
            // Действие при клике на алгоритм
            prefsManager.algorithmIndex = selectedAlgIndex
            restartAnalyzer()
        }

        // Информационные поля оборотов и статуса
        tvRpmValue = TextView(this).apply {
            text = "0 RPM"
            textSize = 36f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        tvStatusValue = TextView(this).apply {
            text = "Ожидание запуска..."
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        
        headerBuilder.infoPanel.addView(tvRpmValue)
        headerBuilder.infoPanel.addView(tvStatusValue)

        // Инициализируем таблицу настроек (мотор, лимит, обновление, плавность)
        uiBuilder = UIBuilder(this, prefsManager) {
            // При изменении любого параметра (например, смене типа мотора 2T/4T/Others)
            // обновляем состояние кнопок алгоритмов (активируем/блокируем нужную пару)
            headerBuilder.updateButtonStates()
            restartAnalyzer()
        }

        // --- ДОБАВЛЯЕМ ВСЁ НА ЭКРАН В СТРОГОМ ПОРЯДКЕ ---
        rootLayout.addView(headerBuilder.topPanel)
        rootLayout.addView(headerBuilder.infoPanel)
        rootLayout.addView(uiBuilder.table)          // Таблица настроек
        rootLayout.addView(headerBuilder.algorithmRow) // Строка алгоритмов (строго под настройками)

        // Копирайт внизу
        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.2"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }
        rootLayout.addView(copyright)
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

        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            selectedAlgorithmIndex = prefsManager.algorithmIndex,
            onUpdate = { rpm, _, _, status ->
                runOnUiThread {
                    tvRpmValue.text = "$rpm RPM"
                    tvStatusValue.text = status
                }
            },
            onError = { err ->
                runOnUiThread {
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    tvStatusValue.text = "Ошибка: $err"
                }
            }
        )
        audioAnalyzer.start()
    }

    private fun stopAnalyzer() {
        if (!isRunning) return
        isRunning = false
        audioAnalyzer.stop()
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
                Toast.makeText(this, "Нужно разрешение на микрофон!", Toast.LENGTH_LONG).show()
                tvStatusValue.text = "Нет доступа к микрофону"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startAnalyzer()
    }

    override fun onPause() {
        super.onPause()
        stopAnalyzer()
    }
}
