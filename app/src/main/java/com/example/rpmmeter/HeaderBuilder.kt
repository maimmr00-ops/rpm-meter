package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class HeaderBuilder(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onAlgorithmSelected: (Int) -> Unit
) {
    val topPanel = LinearLayout(context)
    val infoPanel = LinearLayout(context)
    val algorithmRow = LinearLayout(context)
    
    // Элементы интерфейса для обновления извне
    lateinit var tvRpmValue: TextView
    lateinit var tvMainStatus: TextView
    lateinit var tvDetails: TextView
    lateinit var tvFreqStatus: TextView
    lateinit var vuMeterBar: ProgressBar
    
    private val algorithmButtons = arrayOfNulls<Button>(4)

    init {
        createTopPanel()
        createInfoPanel()
        createAlgorithmRow()
        updateButtonStates()
    }

    private fun createTopPanel() {
        topPanel.orientation = LinearLayout.HORIZONTAL
        topPanel.gravity = Gravity.CENTER_VERTICAL
        topPanel.setPadding(8, 8, 8, 8)

        // Кнопка EXIT
        val btnExit = Button(context).apply {
            text = "EXIT"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1.2f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                (context as? MainActivity)?.finish()
            }
        }

        // Большое поле RPM по центру
        tvRpmValue = TextView(context).apply {
            text = "0"
            textSize = 42f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
        }

        // Кнопка HOLD
        val btnHold = Button(context).apply {
            text = "HOLD"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1.2f).apply {
                setMargins(4, 4, 4, 4)
            }
            var isHold = false
            setOnClickListener {
                isHold = !isHold
                setBackgroundColor(if (isHold) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
                text = if (isHold) "HELD" else "HOLD"
                // Логику удержания можно завязать на паузу обновления в MainActivity
                (context as? MainActivity)?.isHoldActive = isHold
            }
        }

        topPanel.addView(btnExit)
        topPanel.addView(tvRpmValue)
        topPanel.addView(btnHold)
    }

    private fun createInfoPanel() {
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(8, 4, 8, 4)
        infoPanel.gravity = Gravity.CENTER_HORIZONTAL

        val rpmLabel = TextView(context).apply {
            text = "RPM (об / мин)"
            textSize = 11f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        tvMainStatus = TextView(context).apply {
            text = "Ожидание запуска двигателя"
            textSize = 13f
            setTextColor(Color.parseColor("#FFEB3B"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        tvDetails = TextView(context).apply {
            text = "Громкость: 0 | Порог: 10"
            textSize = 11f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
        }

        tvFreqStatus = TextView(context).apply {
            text = "Частота: 0 Гц | Статус: Ниже порога"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
        }

        // VU-метр (Прогресс-бар громкости)
        val vuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 8, 4, 4)
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val vuTitle = TextView(context).apply {
            text = "VU-метр: "
            textSize = 11f
            setTextColor(Color.parseColor("#B0BEC5"))
        }

        vuMeterBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, 24, 1f)
            // Визуальный стиль полосы можно зафиксировать
        }

        vuLayout.addView(vuTitle)
        vuLayout.addView(vuMeterBar)

        infoPanel.addView(rpmLabel)
        infoPanel.addView(tvMainStatus)
        infoPanel.addView(tvDetails)
        infoPanel.addView(tvFreqStatus)
        infoPanel.addView(vuLayout)
    }

    private fun createAlgorithmRow() {
        algorithmRow.orientation = LinearLayout.HORIZONTAL
        algorithmRow.setPadding(4, 6, 4, 6)

        val algNames = arrayOf("Алг 1", "Алг 2", "Алг 3", "Алг 4")
        val currentAlg = prefsManager.algorithmIndex

        for (i in 0..3) {
            val btn = Button(context).apply {
                text = algNames[i]
                textSize = 12f
                setPadding(2, 2, 2, 2)
                layoutParams = LinearLayout.LayoutParams(0, 90, 1f).apply {
                    setMargins(2, 2, 2, 2)
                }
                setOnClickListener {
                    if (prefsManager.isAlgorithmAllowed(i, prefsManager.engineType)) {
                        onAlgorithmSelected(i)
                        updateButtonStates()
                    }
                }
            }
            algorithmButtons[i] = btn
            algorithmRow.addView(btn)
        }
    }

    fun updateButtonStates() {
        val engine = prefsManager.engineType
        val currentAlg = prefsManager.algorithmIndex

        for (i in 0..3) {
            val btn = algorithmButtons[i] ?: continue
            val isAllowed = prefsManager.isAlgorithmAllowed(i, engine)
            
            if (isAllowed) {
                if (i == currentAlg) {
                    btn.setBackgroundColor(Color.parseColor("#00ACC1")) // Активный голубой
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#37474F")) // Доступный темный
                    btn.setTextColor(Color.WHITE)
                }
                btn.isEnabled = true
            } else {
                btn.setBackgroundColor(Color.parseColor("#212121")) // Заблокированный
                btn.setTextColor(Color.parseColor("#616161"))
                btn.isEnabled = false
            }
        }
    }
}
