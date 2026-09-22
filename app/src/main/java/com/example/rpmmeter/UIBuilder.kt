package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HeaderBuilder(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onAlgorithmSelected: (Int) -> Unit
) {

    // Верхняя панель (Заголовок)
    val topPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(8, 8, 8, 8)
    }

    // Информационная панель (Обороты и статус)
    val infoPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8, 8, 8, 8)
        setBackgroundColor(Color.parseColor("#1A1A1A"))
    }

    // Строка выбора алгоритмов (размещается под плавностью)
    val algorithmRow = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8, 8, 8, 8)
        setBackgroundColor(Color.parseColor("#161616"))
    }

    val tvAlgorithmModeLabel = TextView(context).apply {
        text = getModeLabelText()
        textSize = 12f
        setTextColor(Color.parseColor("#00E676"))
        setPadding(4, 0, 0, 4)
    }

    val algorithmButtons = arrayOfNulls<Button>(4)

    init {
        buildHeader()
    }

    private fun buildHeader() {
        // Наполнение заголовка
        val tvTitle = TextView(context).apply {
            text = "RPM Meter 2.2"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        topPanel.addView(tvTitle)

        // Добавляем метку режима в строку алгоритмов
        algorithmRow.addView(tvAlgorithmModeLabel)

        // Создаем кнопки алгоритмов
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val algNames = arrayOf("Zero-Cross", "Autocorrel", "AMDF", "Peak-Time")
        for (i in algNames.indices) {
            val btn = Button(context).apply {
                text = algNames[i]
                textSize = 10f
                setOnClickListener {
                    onAlgorithmSelected(i)
                    updateButtonStyles(i)
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
        algorithmRow.addView(buttonsLayout)
        updateButtonStyles(prefsManager.algorithmIndex)
    }

    fun updateButtonStyles(selectedIndex: Int) {
        for (i in algorithmButtons.indices) {
            if (i == selectedIndex) {
                algorithmButtons[i]?.setBackgroundColor(Color.parseColor("#3F51B5"))
                algorithmButtons[i]?.setTextColor(Color.WHITE)
            } else {
                algorithmButtons[i]?.setBackgroundColor(Color.parseColor("#333333"))
                algorithmButtons[i]?.setTextColor(Color.LTGRAY)
            }
        }
    }

    private fun getModeLabelText(): String {
        return when (prefsManager.engineType) {
            2 -> "режим: 2T (Защита от звона)"
            4 -> "режим: 4T (Автокорреляция)"
            else -> "режим: Others (Гул / Вибрация)"
        }
    }
}
