package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class UIBuilder(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onSettingChanged: () -> Unit
) {

    // Таблица настроек, возвращаемая наружу для добавления в MainActivity
    val table = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8, 8, 8, 8)
        setBackgroundColor(Color.parseColor("#1E1E1E"))
    }

    init {
        buildSettingsTable()
    }

    private fun buildSettingsTable() {
        // --- 1. Ряд: Выбор типа двигателя (2T, 4T, Others) ---
        val motorRow = createRow("мотор:", arrayOf("2T", "4T", "Others")) { index ->
            prefsManager.engineType = when (index) {
                0 -> 2 // 2T
                1 -> 4 // 4T
                else -> 0 // Others
            }
            onSettingChanged()
        }

        // --- 2. Ряд: Лимит оборотов ---
        val limitRow = createRow("лимит:", arrayOf("6k", "12k", "20k")) { index ->
            prefsManager.maxAllowedRpm = when (index) {
                0 -> 6000
                1 -> 12000
                else -> 20000
            }
            onSettingChanged()
        }

        // --- 3. Ряд: Обновление / Размер буфера ---
        val updateRow = createRow("обновление:", arrayOf("Fast", "Norm", "Slow")) { index ->
            prefsManager.audioBufferSize = when (index) {
                0 -> 1280
                1 -> 2560
                else -> 5120
            }
            onSettingChanged()
        }

        // --- 4. Ряд: Плавность отображения (Sharp, Norm, Soft) ---
        val smoothRow = createRow("плавность:", arrayOf("Sharp", "Norm", "Soft")) { index ->
            // ИСПРАВЛЕНО: прямая запись в пресет плавности через prefsManager вместо несуществующего saveSmooth
            prefsManager.smoothPreset = index
            onSettingChanged()
        }

        table.addView(motorRow)
        table.addView(limitRow)
        table.addView(updateRow)
        table.addView(smoothRow)
    }

    /**
     * Вспомогательный метод для создания строки настроек с тремя кнопками выбора.
     */
    private fun createRow(
        labelTitle: String,
        options: Array<String>,
        onSelected: (Int) -> Unit
    ): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }

        val tvLabel = TextView(context).apply {
            text = labelTitle
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        rowLayout.addView(tvLabel)

        val buttons = arrayOfNulls<Button>(options.size)
        for (i in options.indices) {
            val btn = Button(context).apply {
                text = options[i]
                textSize = 11f
                setOnClickListener {
                    onSelected(i)
                    // Подсвечиваем выбранную кнопку в ряду
                    for (j in buttons.indices) {
                        if (j == i) {
                            buttons[j]?.setBackgroundColor(Color.parseColor("#3F51B5"))
                            buttons[j]?.setTextColor(Color.WHITE)
                        } else {
                            buttons[j]?.setBackgroundColor(Color.parseColor("#333333"))
                            buttons[j]?.setTextColor(Color.LTGRAY)
                        }
                    }
                }
            }

            // Начальная подсветка по текущим настройкам
            when (labelTitle) {
                "мотор:" -> {
                    val currentEngine = prefsManager.engineType
                    val isSelected = (i == 0 && currentEngine == 2) || (i == 1 && currentEngine == 4) || (i == 2 && currentEngine == 0)
                    if (isSelected) {
                        btn.setBackgroundColor(Color.parseColor("#3F51B5"))
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.setBackgroundColor(Color.parseColor("#333333"))
                        btn.setTextColor(Color.LTGRAY)
                    }
                }
                "плавность:" -> {
                    if (i == prefsManager.smoothPreset) {
                        btn.setBackgroundColor(Color.parseColor("#3F51B5"))
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.setBackgroundColor(Color.parseColor("#333333"))
                        btn.setTextColor(Color.LTGRAY)
                    }
                }
                else -> {
                    if (i == 1) { // По умолчанию среднее
                        btn.setBackgroundColor(Color.parseColor("#3F51B5"))
                        btn.setTextColor(Color.WHITE)
                    } else {
                        btn.setBackgroundColor(Color.parseColor("#333333"))
                        btn.setTextColor(Color.LTGRAY)
                    }
                }
            }

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(2, 0, 2, 0)
            }
            btn.layoutParams = params
            buttons[i] = btn
            rowLayout.addView(btn)
        }

        return rowLayout
    }
}
