package com.example.rpmmeter

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class HeaderBuilder(
    private val context: Context,
    private val onExit: () -> Unit,
    private val onHoldToggle: () -> Unit,
    private val onMultiplierSelect: (Int) -> Unit
) {
    lateinit var btnExit: Button
    lateinit var btnHold: Button
    lateinit var rpmTextView: TextView
    
    lateinit var statusLine1: TextView
    lateinit var statusLine2: TextView
    lateinit var statusLine3: TextView

    val btnX1 = Button(context).apply { text = "/1"; textSize = 11f }
    val btnX2 = Button(context).apply { text = "/2"; textSize = 11f }
    val btnX3 = Button(context).apply { text = "/3"; textSize = 11f }
    val btnX4 = Button(context).apply { text = "/4"; textSize = 11f }

    // ==========================================
    // СБОРКА ВЕРХНЕЙ ПАНЕЛИ (Кнопка EXIT, Табло RPM, Кнопка HOLD)
    // ==========================================
    fun buildTopPanel(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6) // Вертикальные отступы контейнера
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Левая колонка (Кнопка EXIT)
        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.12f)
        }

        btnExit = Button(context).apply {
            text = "EXIT"
            textSize = 10f
            setPadding(0, 0, 0, 0)
            setOnClickListener { onExit() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        leftCol.addView(btnExit)
        container.addView(leftCol)

        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Центральный блок с цифрами RPM и подписью
        val rpmBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.76f)
        }

        // Главное цифровое табло оборотов (с авторазмером шрифта под ширину)
        rpmTextView = TextView(context).apply {
            text = "0000"
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1 // Запрещаем перенос строк
            
            // Автоматическое растягивание шрифта под ширину контейнера (начиная с Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setAutoSizeTextTypeUniformWithConfiguration(
                    20,   // минимальный размер шрифта (sp)
                    150,  // максимальный размер шрифта (sp)
                    2,    // шаг изменения
                    TypedValue.COMPLEX_UNIT_SP
                )
            } else {
                textSize = 104f // Запасной вариант для старых версий Android
            }
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

        // Правая колонка (Кнопка HOLD)
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.FILL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.12f)
        }

        btnHold = Button(context).apply {
            text = "HOLD"
            textSize = 10f
            setPadding(0, 0, 0, 0)
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

    // ==========================================
    // СБОРКА ИНФОРМАЦИОННОЙ ПАНЕЛИ С МНОЖИТЕЛЯМИ ПО БОКАМ
    // ==========================================
    fun buildInfoPanelWithSides(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 2, 2, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val panelHeight = 44
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        // Левые кнопки множителей (/1, /2)
        val leftMultipliers = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }
        btnX1.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(1) } }
        btnX2.apply { setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { onMultiplierSelect(2) } }
        leftMultipliers.addView(btnX1)
        leftMultipliers.addView(btnX2)
        container.addView(leftMultipliers)

        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Центральная колонка с текстовыми строками статуса и отладки
        val centerTextCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f)
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
            text = "All: 0.0 Гц | Pre-Freq: 0.0 Гц"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        centerTextCol.addView(statusLine3)

        container.addView(centerTextCol)
        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // Правые кнопки множителей (/3, /4)
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
