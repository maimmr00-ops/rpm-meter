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

    val topPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(8, 8, 8, 8)
    }

    val infoPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(8, 8, 8, 8)
        setBackgroundColor(Color.parseColor("#1A1A1A"))
    }

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
        val tvTitle = TextView(context).apply {
            text = "RPM Meter 2.2"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        topPanel.addView(tvTitle)

        algorithmRow.addView(tvAlgorithmModeLabel)

        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val algNames = arrayOf("Zero-Cross", "Autocorrel", "AMDF", "Peak-Time")
        for (i in algNames.indices) {
            val btn = Button(context).apply {
                text = algNames[i]
                textSize = 10f
                setOnClickListener {
                    if (prefsManager.isAlgorithmAllowed(i, prefsManager.engineType)) {
                        onAlgorithmSelected(i)
                        updateButtonStates()
                    }
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
        updateButtonStates()
    }

    /**
     * Обновляет состояние и внешний вид кнопок:
     * Разрешенные для текущего мотора подсвечиваются (активные), неподходящие — блокируются (серые).
     */
    fun updateButtonStates() {
        val currentEngine = prefsManager.engineType
        val selectedIndex = prefsManager.algorithmIndex
        tvAlgorithmModeLabel.text = getModeLabelText()

        for (i in algorithmButtons.indices) {
            val btn = algorithmButtons[i] ?: continue
            val isAllowed = prefsManager.isAlgorithmAllowed(i, currentEngine)

            btn.isEnabled = isAllowed

            if (!isAllowed) {
                // Неподходящий алгоритм для этого мотора — делаем неактивным
                btn.setBackgroundColor(Color.parseColor("#1F1F1F"))
                btn.setTextColor(Color.parseColor("#555555"))
            } else if (i == selectedIndex) {
                // Разрешенный и выбранный в данный момент
                btn.setBackgroundColor(Color.parseColor("#3F51B5"))
                btn.setTextColor(Color.WHITE)
            } else {
                // Разрешенный, но не выбранный
                btn.setBackgroundColor(Color.parseColor("#333333"))
                btn.setTextColor(Color.LTGRAY)
            }
        }
    }

    private fun getModeLabelText(): String {
        return when (prefsManager.engineType) {
            2 -> "режим: 2T (Лучшие: Zero-Cross & AMDF)"
            4 -> "режим: 4T (Лучшие: Autocorrel & Peak)"
            else -> "режим: Others (Лучшие: Zero-Cross & Autocorrel)"
        }
    }
}
