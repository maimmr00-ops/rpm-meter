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
        topPanel.setPadding(0, 0, 0, 4)
        topPanel.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // Кнопка EXIT слева
        val btnExit = Button(context).apply {
            text = "EXIT"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 110, 0.22f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                (context as? MainActivity)?.finish()
            }
        }

        // Большое поле RPM и подпись по центру
        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
            gravity = Gravity.CENTER
        }

        tvRpmValue = TextView(context).apply {
            text = "0"
            textSize = 72f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val rpmLabel = TextView(context).apply {
            text = "RPM (об / мин)"
            textSize = 11f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        centerContainer.addView(tvRpmValue)
        centerContainer.addView(rpmLabel)

        // Кнопка HOLD справа
        val btnHold = Button(context).apply {
            text = "HOLD"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 110, 0.22f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                val activity = (context as? MainActivity) ?: return@setOnClickListener
                activity.isHoldActive = !activity.isHoldActive
                if (activity.isHoldActive) {
                    setBackgroundColor(Color.parseColor("#FF9800"))
                    setTextColor(Color.BLACK)
                } else {
                    setBackgroundColor(Color.parseColor("#424242"))
                    setTextColor(Color.WHITE)
                }
            }
        }

        topPanel.addView(btnExit)
        topPanel.addView(centerContainer)
        topPanel.addView(btnHold)
    }

    private fun createInfoPanel() {
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(4, 2, 4, 4)
        infoPanel.gravity = Gravity.CENTER_HORIZONTAL
        infoPanel.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        tvMainStatus = TextView(context).apply {
            text = "Ожидание запуска двигателя"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        tvDetails = TextView(context).apply {
            text = "Громкость: 0 | Порог: 20"
            textSize = 10f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
        }

        tvFreqStatus = TextView(context).apply {
            text = "Частота: 0 Гц | Статус: Ниже порога"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        // VU-метр на всю ширину
        val vuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 6, 4, 4)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        val vuTitle = TextView(context).apply {
            text = "VU-метр: "
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
        }

        vuMeterBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, 24, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
        }

        vuLayout.addView(vuTitle)
        vuLayout.addView(vuMeterBar)

        infoPanel.addView(tvMainStatus)
        infoPanel.addView(tvDetails)
        infoPanel.addView(tvFreqStatus)
        infoPanel.addView(vuLayout)
    }

    private fun createAlgorithmRow() {
        algorithmRow.orientation = LinearLayout.HORIZONTAL
        algorithmRow.setPadding(2, 4, 2, 4)
        algorithmRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        for (i in 0..3) {
            val btn = Button(context).apply {
                text = "АЛГ ${i + 1}"
                textSize = 11f
                setPadding(1, 1, 1, 1)
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f).apply {
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
                    btn.setBackgroundColor(Color.parseColor("#00ACC1")) // Активный алгоритм (бирюзовый)
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.parseColor("#37474F")) // Доступный
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
