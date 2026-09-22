package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HeaderBuilder(
    private val context: Context,
    private val onExit: () -> Unit,
    private val onHoldToggle: () -> Unit,
    private val onMultiplierSelect: (Int) -> Unit,
    private val onSmoothSelect: (Int) -> Unit
) {
    lateinit var btnExit: Button
    lateinit var btnHold: Button
    lateinit var rpmTextView: TextView
    
    lateinit var statusLine1: TextView
    lateinit var statusLine2: TextView
    lateinit var statusLine3: TextView

    // Кнопки коэффициентов /1, /2 и плавности (Sharp, Norm, Soft)
    val btnX1 = Button(context).apply { text = "/1"; textSize = 10f }
    val btnX2 = Button(context).apply { text = "/2"; textSize = 10f }
    val btnSmoothSharp = Button(context).apply { text = "Sharp"; textSize = 9f }
    val btnSmoothNorm = Button(context).apply { text = "Norm"; textSize = 9f }
    val btnSmoothSoft = Button(context).apply { text = "Soft"; textSize = 9f }

    // Правые кнопки /3, /4
    val btnX3 = Button(context).apply { text = "/3"; textSize = 10f }
    val btnX4 = Button(context).apply { text = "/4"; textSize = 10f }

    fun buildTopPanel(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }

        btnExit = Button(context).apply {
            text = "EXIT"
            textSize = 12f
            setOnClickListener { onExit() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        leftCol.addView(btnExit)
        container.addView(leftCol)

        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        val rpmBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
        }

        rpmTextView = TextView(context).apply {
            text = "00000"
            textSize = 82f
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        rpmBlock.addView(rpmTextView)

        val rpmLabel = TextView(context).apply {
            text = "RPM (об / мин)"
            textSize = 11f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        rpmBlock.addView(rpmLabel)

        container.addView(rpmBlock)
        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.22f)
        }

        btnHold = Button(context).apply {
            text = "HOLD"
            textSize = 12f
            setOnClickListener { onHoldToggle() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        rightCol.addView(btnHold)
        container.addView(rightCol)

        return container
    }

    fun buildInfoPanelWithSides(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val panelHeight = 48
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        // Левая колонка: /1, /2 и переключатели плавности
        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }
        val topMultRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        btnX1.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(1) } }
        btnX2.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(2) } }
        topMultRow.addView(btnX1); topMultRow.addView(btnX2)

        val bottomSmoothRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        btnSmoothSharp.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onSmoothSelect(0) } }
        btnSmoothNorm.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onSmoothSelect(1) } }
        val btnSmoothSoftMini = btnSmoothSoft.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onSmoothSelect(2) } }
        bottomSmoothRow.addView(btnSmoothSharp); bottomSmoothRow.addView(btnSmoothNorm); bottomSmoothRow.addView(btnSmoothSoftMini)

        leftCol.addView(topMultRow)
        leftCol.addView(bottomSmoothRow)
        container.addView(leftCol)

        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Центр: статусы, громкость, частоты All и Pre-Freq
        val centerTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f)
        }

        statusLine1 = TextView(context).apply {
            text = "Ожидание запуска двигателя"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
        }
        centerTextCol.addView(statusLine1)

        statusLine2 = TextView(context).apply {
            text = "Громкость: 0 | Порог: 20"
            textSize = 10f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
        }
        centerTextCol.addView(statusLine2)

        statusLine3 = TextView(context).apply {
            text = "All: 0 Гц | Pre-Freq: 0 Гц"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        centerTextCol.addView(statusLine3)

        container.addView(centerTextCol)
        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Правая колонка: /3 и /4
        val rightMultipliers = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }
        btnX3.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(3) } }
        btnX4.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(4) } }
        rightMultipliers.addView(btnX3)
        rightMultipliers.addView(btnX4)
        container.addView(rightMultipliers)

        return container
    }
}
