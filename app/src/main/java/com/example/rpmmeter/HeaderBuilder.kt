package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object HeaderBuilder {

    data class TopPanelComponents(
        val topPanel: View,
        val infoPanel: View,
        val algorithmRow: View,
        val rpmTextView: TextView,
        val btnHold: Button,
        val btnExit: Button,
        val tvAlgorithmModeLabel: TextView,
        val algorithmButtons: Array<Button?>
    )

    fun buildAll(
        context: Context,
        onExitClick: () -> Unit,
        onHoldClick: () -> Unit,
        onMultiplierClick: (Int) -> Unit,
        onAlgorithmClick: (Int) -> Unit,
        settings: UIBuilder.SettingsButtons
    ): TopPanelComponents {
        
        val rpmTextView = TextView(context).apply {
            text = "00000"
            textSize = 82f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        val btnExit = Button(context).apply {
            text = "EXIT"
            textSize = 12f
            setOnClickListener { onExitClick() }
        }

        val btnHold = Button(context).apply {
            text = "HOLD"
            textSize = 12f
            setOnClickListener { onHoldClick() }
        }

        // 1. Верхняя панель с RPM и кнопками EXIT / HOLD
        val topPanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val leftCol = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }.also { it.addView(btnExit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)) }

        val rpmBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
        }.also {
            it.addView(rpmTextView)
            it.addView(TextView(context).apply {
                text = "RPM (об / мин)"
                textSize = 11f
                setTextColor(Color.parseColor("#80CBC4"))
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
        }

        val rightCol = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }.also { it.addView(btnHold, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)) }

        topPanel.addView(leftCol)
        topPanel.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
        topPanel.addView(rpmBlock)
        topPanel.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
        topPanel.addView(rightCol)

        // 2. Панель статусов и множителей /1 - /4
        val statusLine1 = TextView(context).apply { text = "Ожидание запуска"; textSize = 11f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER }
        val statusLine2 = TextView(context).apply { text = "Громкость: 0"; textSize = 10f; setTextColor(Color.parseColor("#80CBC4")); gravity = Gravity.CENTER }
        val statusLine3 = TextView(context).apply { text = "Pre-Freq: 0 Гц"; textSize = 10f; setTextColor(Color.parseColor("#B0BEC5")); gravity = Gravity.CENTER }

        val centerTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f)
        }.also {
            it.addView(statusLine1)
            it.addView(statusLine2)
            it.addView(statusLine3)
        }

        val panelHeight = 44
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(1, 0, 1, 0) }

        val leftMultipliers = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }
        settings.btnX1.apply { text = "/1"; textSize = 10f; setPadding(0,0,0,0); layoutParams = btnParams; setOnClickListener { onMultiplierClick(1) } }
        settings.btnX2.apply { text = "/2"; textSize = 10f; setPadding(0,0,0,0); layoutParams = btnParams; setOnClickListener { onMultiplierClick(2) } }
        (settings.btnX1.parent as? LinearLayout)?.removeView(settings.btnX1)
        (settings.btnX2.parent as? LinearLayout)?.removeView(settings.btnX2)
        leftMultipliers.addView(settings.btnX1)
        leftMultipliers.addView(settings.btnX2)

        val rightMultipliers = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }
        settings.btnX3.apply { text = "/3"; textSize = 10f; setPadding(0,0,0,0); layoutParams = btnParams; setOnClickListener { onMultiplierClick(3) } }
        settings.btnX4.apply { text = "/4"; textSize = 10f; setPadding(0,0,0,0); layoutParams = btnParams; setOnClickListener { onMultiplierClick(4) } }
        (settings.btnX3.parent as? LinearLayout)?.removeView(settings.btnX3)
        (settings.btnX4.parent as? LinearLayout)?.removeView(settings.btnX4)
        rightMultipliers.addView(settings.btnX3)
        rightMultipliers.addView(settings.btnX4)

        val infoPanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }.also {
            it.addView(leftMultipliers)
            it.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
            it.addView(centerTextCol)
            it.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
            it.addView(rightMultipliers)
        }

        // 3. Строка выбора алгоритмов (Алг 1 - Алг 4)
        val tvAlgorithmModeLabel = TextView(context).apply {
            text = "режим: 2T"
            textSize = 12f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.35f).apply { setMargins(0, 0, 4, 0) }
        }

        val algorithmButtons = arrayOfNulls<Button>(4)
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.65f)
        }
        val algBtnParams = LinearLayout.LayoutParams(0, 40, 1f).apply { setMargins(2, 0, 2, 0) }

        for (i in 0 until 4) {
            val btn = Button(context).apply {
                text = "Алг ${i + 1}"
                textSize = 10f
                setPadding(0, 0, 0, 0)
                layoutParams = algBtnParams
                setOnClickListener { onAlgorithmClick(i) }
            }
            algorithmButtons[i] = btn
            buttonsLayout.addView(btn)
        }

        val algorithmRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 6, 4, 6)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }.also {
            it.addView(tvAlgorithmModeLabel)
            it.addView(buttonsLayout)
        }

        return TopPanelComponents(
            topPanel, infoPanel, algorithmRow, rpmTextView, btnHold, btnExit,
            tvAlgorithmModeLabel, algorithmButtons
        )
    }
}
