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
        val btnX1: Button,
        val btnX2: Button,
        val btnX3: Button,
        val btnX4: Button
    )

    fun buildSettingsTable(
        context: Context,
        prefsManager: PreferencesManager,
        onRefreshUI: () -> Unit,
        volumeStepButtons: Array<Button?>,
        onMultiplierChange: (Int) -> Unit,
        currentMultiplierGetter: () -> Int
    ): SettingsButtons {
        val table = TableLayout(context).apply {
            setPadding(0, 2, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btn2T = Button(context).apply {
            text = "2T"
            setOnClickListener { prefsManager.engineType = 2; onRefreshUI() }
        }
        val btn4T = Button(context).apply {
            text = "4T"
            setOnClickListener { prefsManager.engineType = 4; onRefreshUI() }
        }
        val btnOthers = Button(context).apply {
            text = "Озеро"
            setOnClickListener { prefsManager.engineType = 3; onRefreshUI() }
        }

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

        val btnRateFast = Button(context).apply {
            text = "Fast"
            setOnClickListener { prefsManager.audioBufferSize = 1536; onRefreshUI() }
        }
        val btnRateNorm = Button(context).apply {
            text = "Norm"
            setOnClickListener { prefsManager.audioBufferSize = 2560; onRefreshUI() }
        }
        val btnRateSlow = Button(context).apply {
            text = "Slow"
            setOnClickListener { prefsManager.audioBufferSize = 4096; onRefreshUI() }
        }

        val btnSmoothSharp = Button(context).apply {
            text = "Sharp"
            setOnClickListener { prefsManager.saveSmooth(0.02f, 0.05f); onRefreshUI() }
        }
        val btnSmoothNorm = Button(context).apply {
            text = "Norm"
            setOnClickListener { prefsManager.saveSmooth(0.06f, 0.18f); onRefreshUI() }
        }
        val btnSmoothSoft = Button(context).apply {
            text = "Soft"
            setOnClickListener { prefsManager.saveSmooth(0.15f, 0.40f); onRefreshUI() }
        }

        // Маленькие кнопки множителей x1-x4 для встройки в таблицу
        val btnX1 = Button(context).apply {
            text = "x1"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { onMultiplierChange(1) }
        }
        val btnX2 = Button(context).apply {
            text = "x2"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { onMultiplierChange(2) }
        }
        val btnX3 = Button(context).apply {
            text = "x3"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { onMultiplierChange(3) }
        }
        val btnX4 = Button(context).apply {
            text = "x4"; textSize = 11f; setPadding(0,0,0,0)
            setOnClickListener { onMultiplierChange(4) }
        }

        // Строка с x1-x2 слева и x3-x4 справа
        addMultiplierRow(context, table, "множитель:", btnX1, btnX2, btnX3, btnX4)
        
        addRow(context, table, "мотор:", btn2T, btn4T, btnOthers)
        addRow(context, table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addRow(context, table, "обновление:", btnRateFast, btnRateNorm, btnRateSlow)
        addRow(context, table, "плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)
        addVolumeSquaresRow(context, table, "VU-метр:", volumeStepButtons, prefsManager, onRefreshUI)

        return SettingsButtons(
            table, btn2T, btn4T, btnOthers,
            btnLimit1, btnLimit2, btnLimit3,
            btnRateFast, btnRateNorm, btnRateSlow,
            btnSmoothSharp, btnSmoothNorm, btnSmoothSoft,
            btnX1, btnX2, btnX3, btnX4
        )
    }

    private fun addMultiplierRow(context: Context, table: TableLayout, labelText: String, bX1: Button, bX2: Button, bX3: Button, bX4: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
        }

        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 8, 0)
        }

        // Контейнер на 3 ячейки, чтобы визуально соответствовать остальным строкам таблицы
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cellParam = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }

        // Левая ячейка: контейнер для x1 и x2
        const val H_BTN_HEIGHT = 42
        val leftLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = cellParam
        }
        val halfParam = LinearLayout.LayoutParams(0, H_BTN_HEIGHT, 1f).apply { setMargins(1, 0, 1, 0) }
        bX1.layoutParams = halfParam
        bX2.layoutParams = halfParam
        leftLayout.addView(bX1)
        leftLayout.addView(bX2)

        // Средняя ячейка (пустая или для баланса)
        val middleView = View(context).apply {
            layoutParams = cellParam
        }

        // Правая ячейка: контейнер для x3 и x4
        val rightLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = cellParam
        }
        val rightHalfParam = LinearLayout.LayoutParams(0, H_BTN_HEIGHT, 1f).apply { setMargins(1, 0, 1, 0) }
        bX3.layoutParams = rightHalfParam
        bX4.layoutParams = rightHalfParam
        rightLayout.addView(bX3)
        rightLayout.addView(bX4)

        container.addView(leftLayout)
        container.addView(middleView)
        container.addView(rightLayout)

        container.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)

        row.addView(label)
        row.addView(container)
        table.addView(row)
    }

    private fun addRow(context: Context, table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 2)
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

        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(2, 0, 2, 0)
        }
        b1.layoutParams = p
        b2.layoutParams = p
        b3.layoutParams = p

        bLayout.addView(b1)
        bLayout.addView(b2)
        bLayout.addView(b3)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)

        row.addView(label)
        row.addView(bLayout)
        table.addView(row)
    }

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

            val p = LinearLayout.LayoutParams(0, 42, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
            squareBtn.layoutParams = p

            volumeStepButtons[i] = squareBtn
            squaresLayout.addView(squareBtn)
        }

        squaresLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 3f)

        row.addView(label)
        row.addView(squaresLayout)
        table.addView(row)
    }

    fun getThresholdForSquare(index: Int): Int {
        return when (index) {
            0 -> 20
            1 -> { 150 }
            2 -> 400
            3 -> 800
            4 -> 1400
            5 -> 2200
            6 -> 3200
            7 -> 4800
            8 -> 6800
            else -> 9000
        }
    }
}
