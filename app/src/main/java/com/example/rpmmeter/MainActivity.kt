package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Менеджер для сохранения настроек в SharedPreferences (память устройства)
    private lateinit var prefsManager: PreferencesManager
    
    // Анализатор звука, работающий в фоновом потоке
    private lateinit var audioAnalyzer: AudioAnalyzer
    
    // Главный вертикальный контейнер всего экрана
    private lateinit var rootLayout: LinearLayout
    
    // Текстовые метки для вывода режима (2T, 4T, Others) и текущих оборотов
    private lateinit var tvAlgorithmModeLabel: TextView
    private lateinit var tvRpmValue: TextView
    private lateinit var tvStatusValue: TextView
    
    // Массив кнопок для переключения алгоритмов (0 до 3)
    private val algorithmButtons = arrayOfNulls<Button>(4)
    
    // Флаг состояния работы анализатора
    private var isRunning = false

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем хранилище настроек
        prefsManager = PreferencesManager(this)

        // 1. Создаем корневой layout (экран приложения с темным фоном)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(12, 12, 12, 12)
        }
        setContentView(rootLayout)

        // 2. Строим пользовательский интерфейс по блокам
        setupUI()

        // 3. Проверяем разрешение на микрофон и запускаем анализ
        checkAudioPermissionAndStart()
    }

    /**
     * Сборка интерфейса: создание панелей, кнопок и размещение их в нужном порядке.
     */
    private fun setupUI() {
        // --- БЛОК 1: Верхняя панель (Заголовок) ---
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        val tvTitle = TextView(this).apply {
            text = "RPM Meter 2.2"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        topPanel.addView(tvTitle)

        // --- БЛОК 2: Информационная панель (Обороты и статус) ---
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        
        tvRpmValue = TextView(this).apply {
            text = "0 RPM"
            textSize = 36f
            setTextColor(Color.parseColor("#00E676")) // Зеленый цвет цифр
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        tvStatusValue = TextView(this).apply {
            text = "Ожидание запуска..."
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        
        infoPanel.addView(tvRpmValue)
        infoPanel.addView(tvStatusValue)

        // --- БЛОК 3: Таблица настроек (Мотор, Лимит, Обновление, Плавность) ---
        val settingsTable = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        val tvSmoothLabel = TextView(this).apply {
            text = "Параметры и Плавность"
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        settingsTable.addView(tvSmoothLabel)

        // --- БЛОК 4: Строка алгоритмов (Размещается строго ПОД плавностью/настройками) ---
        val algorithmRowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#161616"))
        }

        // Подпись текущего режима (динамически отображает 2T, 4T или Others)
        tvAlgorithmModeLabel = TextView(this).apply {
            text = updateModeLabelText()
            textSize = 12f
            setTextColor(Color.parseColor("#00E676"))
            setPadding(4, 0, 0, 4)
        }
        algorithmRowContainer.addView(tvAlgorithmModeLabel)

        // Горизонтальный ряд из 4 кнопок с понятными названиями алгоритмов
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Реальные названия алгоритмов для кнопок
        val algNames = arrayOf("Zero-Cross", "Autocorrel", "AMDF", "Peak-Time")
        for (i in algNames.indices) {
            val btn = Button(this).apply {
                text = algNames[i] // Название алгоритма на кнопке
                textSize = 10f     // Компактный шрифт, чтобы текст поместился
                setOnClickListener {
                    // При нажатии сохраняем индекс алгоритма (0, 1, 2 или 3)
                    prefsManager.algorithmIndex = i
                    refreshAlgorithmButtonsUI() // Подсвечиваем активную кнопку
                    restartAnalyzer()           // Перезапускаем анализатор с новой математикой
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(2, 0, 2, 0)
            }
            btn.layoutParams = params
            algorithmButtons[i] = btn
            buttonsLayout.addView(btn)
        }
        algorithmRowContainer.addView(buttonsLayout)
        refreshAlgorithmButtonsUI() // Первичная подсветка

        // --- СТРОГИЙ ПОРЯДОК ДОБАВЛЕНИЯ ЭЛЕМЕНТОВ НА ЭКРАН ---
        rootLayout.addView(topPanel)
        rootLayout.addView(infoPanel)
        rootLayout.addView(settingsTable)         // 1. Сначала настройки (Плавность)
        rootLayout.addView(algorithmRowContainer) // 2. Строка алгоритмов — строго ПОД плавностью

        // --- БЛОК 5: Копирайт в самом низу ---
        val copyright = TextView(this).apply {
            text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.2"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }
        rootLayout.addView(copyright)
    }

    /**
     * Формирует текст метки режима в зависимости от выбранного двигателя в настройках.
     */
    private fun updateModeLabelText(): String {
        return when (prefsManager.engineType) {
            2 -> "режим: 2T (Защита от звона)"
            4 -> "режим: 4T (Автокорреляция)"
            else -> "режим: Others (Гул / Вибрация)"
        }
    }

    /**
     * Визуальная подсветка активной кнопки алгоритма (синий цвет для выбранной, темный для остальных).
     */
    private fun refreshAlgorithmButtonsUI() {
        val currentIndex = prefsManager.algorithmIndex
        for (i in algorithmButtons.indices) {
            if (i == currentIndex) {
                algorithmButtons[i]?.setBackgroundColor(Color.parseColor("#3F51B5")) // Активная
                algorithmButtons[i]?.setTextColor(Color.WHITE)
            } else {
                algorithmButtons[i]?.setBackgroundColor(Color.parseColor("#333333")) // Неактивная
                algorithmButtons[i]?.setTextColor(Color.LTGRAY)
            }
        }
    }

    /**
     * Проверка разрешений на использование микрофона.
     */
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

    /**
     * Запуск фонового анализатора звука.
     */
    private fun startAnalyzer() {
        if (isRunning) return
        isRunning = true

        audioAnalyzer = AudioAnalyzer(
            prefsManager = prefsManager,
            selectedAlgorithmIndex = prefsManager.algorithmIndex,
            onUpdate = { rpm, _, _, status ->
                // Обновляем UI в главном потоке
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

    /**
     * Остановка анализатора.
     */
    private fun stopAnalyzer() {
        if (!isRunning) return
        isRunning = false
        audioAnalyzer.stop()
    }

    /**
     * Перезапуск анализатора (при смене алгоритма или настроек).
     */
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
        startAnalyzer() // Запускаем при возврате в приложение
    }

    override fun onPause() {
        super.onPause()
        stopAnalyzer()  // Останавливаем, когда приложение сворачивается
    }
}
