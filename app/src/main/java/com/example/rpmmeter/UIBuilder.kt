package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

object UIBuilder {

    // 01: Контейнер-структура для всех интерактивных кнопок настроек таблицы (теперь 8 алгоритмов)
    data class SettingsButtons(
        val table: TableLayout,
        val btn2T: Button,
        val btn4T: Button,
        val btnOthers: Button,
        val btnLimit1: Button,
        val btnLimit2: Button,
        val btnLimit3: Button,
        val btnRateFast: Button,
        val btnRateNorm: Button,
        val btnRateSlow: Button,
        val btnSmoothSharp: Button,
        val btnSmoothNorm: Button,
        val btnSmoothSoft: Button,
        val btnAlg1: Button,
        val btnAlg2: Button,
        val btnAlg3: Button,
        val btnAlg4: Button,
        val btnAlg5: Button,
        val btnAlg6: Button,
        val btnAlg7: Button,
        val btnAlg8: Button
    )

    fun buildSettingsTable(
        context: Context,
        prefsManager: PreferencesManager,
        onRefreshUI: () -> Unit,
        volumeStepButtons: Array<Button?>
    ): SettingsButtons {
        // 02: Основной компоновщик таблицы параметров с весом для заполнения экрана
        val table = TableLayout(context).apply {
            setPadding(0, 4, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 
                1f
            )
        }

        // 03: Кнопки выбора типа двигателя (2T, 4T, Others)
        val btn2T = Button(context).apply {
            text = "2T"
            setOnClickListener { prefsManager.engineType = 2; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }
        val btn4T = Button(context).apply {
            text = "4T"
            setOnClickListener { prefsManager.engineType = 4; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }
        val btnOthers = Button(context).apply {
            text = "Others"
            setOnClickListener { prefsManager.engineType = 3; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }

        // 04: Кнопки лимитов оборотов (6k, 12k, 20k)
        val btnLimit1 = Button(context).apply {
            text = "6k"
            setOnClickListener { prefsManager.maxAllowedRpm = 6000; onRefreshUI() }
        }
        val btnLimit2 = Button(context).apply {
            text = "12k"
            setOnClickListener { prefsManager.maxAllowedRpm = 12000; onRefreshUI() }
        }
        val btnLimit3 = Button(context).apply {
            text = "20k"
            setOnClickListener { prefsManager.maxAllowedRpm = 20000; onRefreshUI() }
        }

        // 05: Кнопки скорости / размера буфера аудио (Fast, Norm, Slow)
        val btnRateFast = Button(context).apply {
            text = "Fast"
            setOnClickListener { prefsManager.audioBufferSize = 1536; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }
        val btnRateNorm = Button(context).apply {
            text = "Norm"
            setOnClickListener { prefsManager.audioBufferSize = 2560; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }
        val btnRateSlow = Button(context).apply {
            text = "Slow"
            setOnClickListener { prefsManager.audioBufferSize = 4096; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        }

        // 06: Кнопки управления плавностью тахометра (Sharp, Norm, Soft)
        val btnSmoothSharp = Button(context).apply {
            text = "Sharp"
            setOnClickListener { prefsManager.smoothPreset = 0; onRefreshUI() }
        }
        val btnSmoothNorm = Button(context).apply {
            text = "Norm"
            setOnClickListener { prefsManager.smoothPreset = 1; onRefreshUI() }
        }
        val btnSmoothSoft = Button(context).apply {
            text = "Soft"
            setOnClickListener { prefsManager.smoothPreset = 2; onRefreshUI() }
        }

        // 07: Кнопки выбора алгоритмов анализа (1-4 ряд)
        val btnAlg1 = Button(context).apply { text = "Zero-X"; setOnClickListener { prefsManager.algorithmIndex = 0; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg2 = Button(context).apply { text = "AutoCorr"; setOnClickListener { prefsManager.algorithmIndex = 1; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg3 = Button(context).apply { text = "Spectral"; setOnClickListener { prefsManager.algorithmIndex = 2; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg4 = Button(context).apply { text = "Hybrid"; setOnClickListener { prefsManager.algorithmIndex = 3; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }

        // Кнопки выбора алгоритмов анализа (5-8 ряд)
        val btnAlg5 = Button(context).apply { text = "YIN"; setOnClickListener { prefsManager.algorithmIndex = 4; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg6 = Button(context).apply { text = "HPS"; setOnClickListener { prefsManager.algorithmIndex = 5; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg7 = Button(context).apply { text = "AMDF"; setOnClickListener { prefsManager.algorithmIndex = 6; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }
        val btnAlg8 = Button(context).apply { text = "Comb"; setOnClickListener { prefsManager.algorithmIndex = 7; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() } }

        // 08: Сборка строк таблицы в строгом порядке (теперь алгоритмы занимают 2 строки)
        addRow(context, table, "мотор:", btn2T, btn4T, btnOthers)
        addRow(context, table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(context, table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(context, table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)
        addFourRow(context, table, "алг. 1-4:", btnAlg1, btnAlg2, btnAlg3, btnAlg4)
        addFourRow(context, table, "алг. 5-8:", btnAlg5, btnAlg6, btnAlg7, btnAlg8)
        
        // 09: VU-метр (индикатор громкости из 10 квадратов в самом низу таблицы)
        addVolumeSquaresRow(context, table, "VU-метр:", volumeStepButtons, prefsManager, onRefreshUI)

        return SettingsButtons(
            table, btn2T, btn4T, btnOthers,
            btnLimit1, btnLimit2, btnLimit3,
            btnRateFast, btnRateNorm, btnRateSlow,
            btnSmoothSharp, btnSmoothNorm, btnSmoothSoft,
            btnAlg1, btnAlg2, btnAlg3, btnAlg4,
            btnAlg5, btnAlg6, btnAlg7, btnAlg8
        )
    }

    // 10: Вспомогательный метод добавления строки с тремя кнопками
    private fun addRow(context: Context, table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }
        val bLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }
        b1.layoutParams = p; b2.layoutParams = p; b3.layoutParams = p
        bLayout.addView(b1); bLayout.addView(b2); bLayout.addView(b3)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)

        row.addView(label); row.addView(bLayout)
        table.addView(row)
    }

    // 11: Вспомогательный метод добавления строки с четырьмя кнопками
    private fun addFourRow(context: Context, table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button, b4: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }
        val bLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }
        b1.layoutParams = p; b2.layoutParams = p; b3.layoutParams = p; b4.layoutParams = p
        bLayout.addView(b1); bLayout.addView(b2); bLayout.addView(b3); bLayout.addView(b4)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)

        row.addView(label); row.addView(bLayout)
        table.addView(row)
    }

    // 12: Генерация ряда квадратов для шкалы громкости
    private fun addVolumeSquaresRow(
        context: Context, 
        table: TableLayout, 
        labelText: String, 
        volumeStepButtons: Array<Button?>,
        prefsManager: PreferencesManager,
        onRefreshUI: () -> Unit
    ) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }
        val squaresLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until 10) {
            val thresholdValue = getThresholdForSquare(i)
            val squareBtn = Button(context).apply {
                text = ""
                textSize = 10f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minimumWidth = 0
                setOnClickListener {
                    prefsManager.minVolumeThreshold = thresholdValue
                    onRefreshUI()
                }
            }
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
            squareBtn.layoutParams = p
            volumeStepButtons[i] = squareBtn
            squaresLayout.addView(squareBtn)
        }
        squaresLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)
        row.addView(label); row.addView(squaresLayout)
        table.addView(row)
    }

    // 13: Управление цветами и активностью кнопок всех 8 алгоритмов
    fun updateAlgorithmButtons(
        prefsManager: PreferencesManager,
        btnAlg1: Button,
        btnAlg2: Button,
        btnAlg3: Button,
        btnAlg4: Button,
        btnAlg5: Button,
        btnAlg6: Button,
        btnAlg7: Button,
        btnAlg8: Button
    ) {
        val alg = prefsManager.algorithmIndex
        val engine = prefsManager.engineType

        val buttons = arrayOf(btnAlg1, btnAlg2, btnAlg3, btnAlg4, btnAlg5, btnAlg6, btnAlg7, btnAlg8)

        for (i in buttons.indices) {
            val isAllowed = prefsManager.isAlgorithmAllowed(i, engine)
            val isSelected = (alg == i)

            buttons[i].setBackgroundColor(when {
                isSelected -> Color.parseColor("#00BCD4")
                !isAllowed -> Color.parseColor("#212121")
                else -> Color.parseColor("#424242")
            })
            buttons[i].setTextColor(if (isAllowed) Color.WHITE else Color.parseColor("#616161"))
            buttons[i].isEnabled = isAllowed
        }
    }

    val thresholdValues = intArrayOf(20, 150, 400, 800, 1400, 2200, 3200, 4800, 6800, 9000)

    fun getThresholdForSquare(index: Int): Int {
        return if (index in thresholdValues.indices) thresholdValues[index] else 20
    }
}
