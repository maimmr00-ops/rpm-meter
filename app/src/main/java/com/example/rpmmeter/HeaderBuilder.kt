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
        topPanel.setPadding(4, 4, 4, 4)

        // Кнопка EXIT слева
        val btnExit = Button(context).apply {
            text = "EXIT"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 130, 1.5f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                (context as? MainActivity)?.finish()
            }
        }

        // Центральная колонка с большим значением RPM
        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            gravity = Gravity.CENTER
        }

        tvRpmValue = TextView(context).apply {
            text = "0"
            textSize = 42f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        centerContainer.addView(tvRpmValue)

        // Кнопка HOLD справа
        val btnHold = Button(context).apply {
            text = "HOLD"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 130, 1.5f).apply {
                setMargins(2, 2, 2, 2)
            }
            var isHold = false
            setOnClickListener {
                isHold = !isHold
                setBackgroundColor(if (isHold) Color.parseColor("#FF9800") else Color.parseColor("#424242"))
                text = if (isHold) "HELD" else "HOLD"
                (context as? MainActivity)?.isHoldActive = isHold
            }
        }

        topPanel.addView(btnExit)
        topPanel.addView(centerContainer)
        topPanel.addView(btnHold)
    }

    private fun createInfoPanel() {
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.setPadding(4, 2, 4, 2)
        infoPanel.gravity = Gravity.CENTER_HORIZONTAL

        val rpmLabel = TextView(context).apply {
            text = "RPM (об / мин)"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        tvMainStatus = TextView(context).apply {
            text = "Ожидание запуска двигателя"
            textSize = 12f
            setTextColor(Color.parseColor("#FFEB3B"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        tvDetails = TextView(context).apply {
            text = "Громкость: 0 | Порог: 20"
            textSize = 10f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
        }

        tvFreqStatus = TextView(context).apply {
            text = "Частота: 0 Гц | Статус: Ниже порога"
            textSize = 10f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
        }

        // VU-метр
        val vuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 4, 4, 2)
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val vuTitle = TextView(context).apply {
            text = "VU-метр: "
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        vuMeterBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, 20, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
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
        algorithmRow.setPadding(2, 4, 2, 4)

        for (i in 0..3) {
            val btn = Button(context).apply {
                text = "Алг ${i + 1}"
                textSize = 11f
                setPadding(1, 1, 1, 1)
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f).apply {
                    setMargins(1, 1, 1, 1)
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
                    btn.setBackgroundColor(Color.parseColor("#00ACC1")) // Активный
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
