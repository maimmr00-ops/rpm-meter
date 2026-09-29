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
        val btnRateTurbo: Button,
        val btnRateFast: Button,
        val btnRateNorm: Button,
        val btnRateSlow: Button,
        val btnSmoothOff: Button,
        val btnSmoothSharp: Button,
        val btnSmoothNorm: Button,
        val btnSmoothSoft: Button,
        val btnZeroX: Button,
        val btnAutoCorr: Button,
        val btnSpectral: Button,
        val btnHybrid: Button,
        val btnYin: Button,
        val btnHps: Button,
        val btnAmdf: Button,
        val btnComb: Button,
        val btnAudioMic: Button,
        val btnAudioVoice: Button,
        val btnAudioRaw: Button
    )

    private fun createButton(context: Context, textVal: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = textVal
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            setPadding(2, 0, 2, 0)
            setOnClickListener { onClick() }
        }
    }

    fun buildSettingsTable(
        context: Context,
        prefsManager: PreferencesManager,
        onRefreshUI: () -> Unit,
        volumeStepButtons: Array<Button?>
    ): SettingsButtons {
        val table = TableLayout(context).apply {
            setPadding(0, 2, 0, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 
                1f
            )
        }

        // Кнопки двигателя
        val btn2T = createButton(context, "2T") { prefsManager.engineType = 2; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btn4T = createButton(context, "4T") { prefsManager.engineType = 4; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnOthers = createButton(context, "Others") { prefsManager.engineType = 3; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }

        // Кнопки лимитов
        val btnLimit1 = createButton(context, "6k") { prefsManager.maxAllowedRpm = 6000; onRefreshUI() }
        val btnLimit2 = createButton(context, "12k") { prefsManager.maxAllowedRpm = 12000; onRefreshUI() }
        val btnLimit3 = createButton(context, "20k") { prefsManager.maxAllowedRpm = 20000; onRefreshUI() }

        // Кнопки обновления (Turbo теперь устанавливает 1024)
        val btnRateTurbo = createButton(context, "Turbo") { prefsManager.audioBufferSize = 1024; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnRateFast = createButton(context, "Fast") { prefsManager.audioBufferSize = 1536; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnRateNorm = createButton(context, "Norm") { prefsManager.audioBufferSize = 2560; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnRateSlow = createButton(context, "Slow") { prefsManager.audioBufferSize = 4096; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }

        // Кнопки плавности (4 штуки: Off, Sharp, Norm, Soft)
        val btnSmoothOff = createButton(context, "Off") { prefsManager.smoothPreset = 3; onRefreshUI() }
        val btnSmoothSharp = createButton(context, "Sharp") { prefsManager.smoothPreset = 0; onRefreshUI() }
        val btnSmoothNorm = createButton(context, "Norm") { prefsManager.smoothPreset = 1; onRefreshUI() }
        val btnSmoothSoft = createButton(context, "Soft") { prefsManager.smoothPreset = 2; onRefreshUI() }

        // Кнопки алгоритмов
        val btnZeroX = createButton(context, "Zero-X") { prefsManager.algorithmIndex = 0; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnAutoCorr = createButton(context, "AutoCorr") { prefsManager.algorithmIndex = 1; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnSpectral = createButton(context, "Spectral") { prefsManager.algorithmIndex = 2; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnHybrid = createButton(context, "Hybrid") { prefsManager.algorithmIndex = 3; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }

        val btnYin = createButton(context, "YIN") { prefsManager.algorithmIndex = 4; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnHps = createButton(context, "HPS") { prefsManager.algorithmIndex = 5; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnAmdf = createButton(context, "AMDF") { prefsManager.algorithmIndex = 6; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnComb = createButton(context, "Comb") { prefsManager.algorithmIndex = 7; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }

        // Кнопки выбора аудиовхода
        val btnAudioMic = createButton(context, "MIC") { prefsManager.audioSource = 0; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnAudioVoice = createButton(context, "VOICE") { prefsManager.audioSource = 1; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }
        val btnAudioRaw = createButton(context, "RAW") { prefsManager.audioSource = 2; onRefreshUI(); (context as? MainActivity)?.restartAnalyzer() }

        // Сборка строк таблицы
        addRow(context, table, "мотор:", btn2T, btn4T, btnOthers)
        addRow(context, table, "лимит:", btnLimit1, btnLimit2, btnLimit3)
        addFourRow(context, table, "обновление:", btnRateTurbo, btnRateFast, btnRateNorm, btnRateSlow)
        addFourRow(context, table, "плавность:", btnSmoothOff, btnSmoothSharp, btnSmoothNorm, btnSmoothSoft)
        addFourRow(context, table, "алг. 1-4:", btnZeroX, btnAutoCorr, btnSpectral, btnHybrid)
        addFourRow(context, table, "алг. 5-8:", btnYin, btnHps, btnAmdf, btnComb)
        
        // VU-метр
        addVolumeSquaresRow(context, table, "VU-метр:", volumeStepButtons, prefsManager, onRefreshUI)

        // Вход
        addRow(context, table, "вход:", btnAudioMic, btnAudioVoice, btnAudioRaw)

        return SettingsButtons(
            table, btn2T, btn4T, btnOthers,
            btnLimit1, btnLimit2, btnLimit3,
            btnRateTurbo, btnRateFast, btnRateNorm, btnRateSlow,
            btnSmoothOff, btnSmoothSharp, btnSmoothNorm, btnSmoothSoft,
            btnZeroX, btnAutoCorr, btnSpectral, btnHybrid,
            btnYin, btnHps, btnAmdf, btnComb,
            btnAudioMic, btnAudioVoice, btnAudioRaw
        )
    }

    private fun addRow(context: Context, table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 1, 0, 1)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 6, 0)
        }
        val bLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }
        b1.layoutParams = p; b2.layoutParams = p; b3.layoutParams = p
        bLayout.addView(b1); bLayout.addView(b2); bLayout.addView(b3)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)

        row.addView(label); row.addView(bLayout)
        table.addView(row)
    }

    private fun addFourRow(context: Context, table: TableLayout, labelText: String, b1: Button, b2: Button, b3: Button, b4: Button) {
        val row = TableRow(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 1, 0, 1)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 6, 0)
        }
        val bLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val p = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }
        b1.layoutParams = p; b2.layoutParams = p; b3.layoutParams = p; b4.layoutParams = p
        bLayout.addView(b1); bLayout.addView(b2); bLayout.addView(b3); bLayout.addView(b4)
        bLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)

        row.addView(label); row.addView(bLayout)
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
            setPadding(0, 1, 0, 1)
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val label = TextView(context).apply {
            text = labelText
            textSize = 12f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 6, 0)
        }
        val squaresLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until 10) {
            val thresholdValue = getThresholdForSquare(i)
            val squareBtn = Button(context).apply {
                text = ""
                textSize = 9f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minimumWidth = 0
                setOnClickListener {
                    prefsManager.minVolumeThreshold = thresholdValue
                    onRefreshUI()
                }
            }
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(1, 0, 1, 0)
            }
            squareBtn.layoutParams = p
            volumeStepButtons[i] = squareBtn
            squaresLayout.addView(squareBtn)
        }
        squaresLayout.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 3f)
        row.addView(label); row.addView(squaresLayout)
        table.addView(row)
    }

    fun updateAlgorithmButtons(
        prefsManager: PreferencesManager,
        btnZeroX: Button,
        btnAutoCorr: Button,
        btnSpectral: Button,
        btnHybrid: Button,
        btnYin: Button,
        btnHps: Button,
        btnAmdf: Button,
        btnComb: Button
    ) {
        val alg = prefsManager.algorithmIndex
        val engine = prefsManager.engineType

        val buttons = arrayOf(btnZeroX, btnAutoCorr, btnSpectral, btnHybrid, btnYin, btnHps, btnAmdf, btnComb]

        for (i in buttons.indices) {
            val isAllowed = prefsManager.isAlgorithmAllowed(i, engine)
            val isSelected = (alg == i)

            buttons[i].setBackgroundColor(when {
                isSelected -> Color.parseColor("#00BCD4")
                !isAllowed -> Color.parseColor("#212121")
                else -> Color.parseColor("#424242")
            })
            buttons[i].setTextColor(if (isAllowed) Color.WHITE else Color.parseColor("#616161"))
            buttons[i].isEnabled = isAllowed
        }
    }

    val thresholdValues = intArrayOf(20, 150, 400, 800, 1400, 2200, 3200, 4800, 6800, 9000)

    fun getThresholdForSquare(index: Int): Int {
        return if (index in thresholdValues.indices) thresholdValues[index] else 20
    }
                              }
                              
