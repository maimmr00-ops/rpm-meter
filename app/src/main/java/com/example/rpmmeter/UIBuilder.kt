package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

object UIBuilder {

    class SettingsButtons(
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
        val btnSmoothSoft: Button
    )

    fun getThresholdForSquare(index: Int): Int {
        return when (index) {
            0 -> 10
            1 -> 20
            2 -> 35
            3 -> 50
            4 -> 70
            5 -> 100
            6 -> 140
            7 -> 200
            8 -> 300
            9 -> 450
            else -> 20
        }
    }

    fun buildSettingsTable(
        context: Context,
        prefsManager: PreferencesManager,
        onRefreshUI: () -> Unit,
        volumeStepButtons: Array<Button?>
    ): SettingsButtons {

        val table = TableLayout(context).apply {
            setPadding(0, 4, 0, 4)
            isStretchAllColumns = true
        }

        // Кнопки настроек
        val btn2T = Button(context).apply { text = "2T"; textSize = 12f }
        val btn4T = Button(context).apply { text = "4T"; textSize = 12f }
        val btnOthers = Button(context).apply { text = "OTHERS"; textSize = 11f }

        val btnLimit1 = Button(context).apply { text = "6K"; textSize = 12f }
        val btnLimit2 = Button(context).apply { text = "12K"; textSize = 12f }
        val btnLimit3 = Button(context).apply { text = "20K"; textSize = 12f }

        val btnRateFast = Button(context).apply { text = "FAST"; textSize = 12f }
        val btnRateNorm = Button(context).apply { text = "NORM"; textSize = 12f }
        val btnRateSlow = Button(context).apply { text = "SLOW"; textSize = 12f }

        val btnSmoothSharp = Button(context).apply { text = "SHARP"; textSize = 12f }
        val btnSmoothNorm = Button(context).apply { text = "NORM"; textSize = 12f }
        val btnSmoothSoft = Button(context).apply { text = "SOFT"; textSize = 12f }

        // Логика переключения
        btn2T.setOnClickListener { prefsManager.engineType = 2; onRefreshUI() }
        btn4T.setOnClickListener { prefsManager.engineType = 4; onRefreshUI() }
        btnOthers.setOnClickListener { prefsManager.engineType = 3; onRefreshUI() }

        btnLimit1.setOnClickListener { prefsManager.maxAllowedRpm = 6000; onRefreshUI() }
        btnLimit2.setOnClickListener { prefsManager.maxAllowedRpm = 12000; onRefreshUI() }
        btnLimit3.setOnClickListener { prefsManager.maxAllowedRpm = 20000; onRefreshUI() }

        btnRateFast.setOnClickListener { prefsManager.audioBufferSize = 1536; (context as? MainActivity)?.restartAnalyzer(); onRefreshUI() }
        btnRateNorm.setOnClickListener { prefsManager.audioBufferSize = 2560; (context as? MainActivity)?.restartAnalyzer(); onRefreshUI() }
        btnRateSlow.setOnClickListener { prefsManager.audioBufferSize = 4096; (context as? MainActivity)?.restartAnalyzer(); onRefreshUI() }

        btnSmoothSharp.setOnClickListener { prefsManager.smoothPreset = 0; onRefreshUI() }
        btnSmoothNorm.setOnClickListener { prefsManager.smoothPreset = 1; onRefreshUI() }
        btnSmoothSoft.setOnClickListener { prefsManager.smoothPreset = 2; onRefreshUI() }

        fun createRow(labelStr: String, b1: Button, b2: Button, b3: Button): TableRow {
            val row = TableRow(context).apply {
                setPadding(0, 2, 0, 2)
            }
            val label = TextView(context).apply {
                text = labelStr
                textSize = 11f
                setTextColor(Color.parseColor("#B0BEC5"))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(2, 0, 4, 0)
            }
            
            val params = TableRow.LayoutParams(0, 90, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            b1.layoutParams = params
            b2.layoutParams = params
            b3.layoutParams = params

            row.addView(label)
            row.addView(b1)
            row.addView(b2)
            row.addView(b3)
            return row
        }

        table.addView(createRow("мотор:", btn2T, btn4T, btnOthers))
        table.addView(createRow("лимит:", btnLimit1, btnLimit2, btnLimit3))
        table.addView(createRow("обновление:", btnRateFast, btnRateNorm, btnRateSlow))
        table.addView(createRow("плавность:", btnSmoothSharp, btnSmoothNorm, btnSmoothSoft))

        // Строка квадратов чувствительности (порог)
        val squaresRow = TableRow(context).apply {
            setPadding(0, 4, 0, 2)
        }
        val squaresLabel = TextView(context).apply {
            text = "порог:"
            textSize = 11f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 0, 4, 0)
        }
        squaresRow.addView(squaresLabel)

        val squaresContainer = TableRow(context).apply {
            layoutParams = TableRow.LayoutParams(0, 70, 3f)
        }
        
        for (i in 0 until 10) {
            val sqBtn = Button(context).apply {
                text = ""
                setPadding(0, 0, 0, 0)
                layoutParams = TableRow.LayoutParams(0, 70, 1f).apply {
                    setMargins(1, 2, 1, 2)
                }
                setOnClickListener {
                    val newThreshold = getThresholdForSquare(i)
                    prefsManager.minVolumeThreshold = newThreshold
                    onRefreshUI()
                }
            }
            volumeStepButtons[i] = sqBtn
            squaresContainer.addView(sqBtn)
        }
        squaresRow.addView(squaresContainer)
        table.addView(squaresRow)

        return SettingsButtons(
            table, btn2T, btn4T, btnOthers,
            btnLimit1, btnLimit2, btnLimit3,
            btnRateFast, btnRateNorm, btnRateSlow,
            btnSmoothSharp, btnSmoothNorm, btnSmoothSoft
        )
    }
}
