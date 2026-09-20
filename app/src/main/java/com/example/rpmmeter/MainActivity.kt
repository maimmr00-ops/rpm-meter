    private fun buildInfoPanelWithSides(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val panelHeight = 44
        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(1, 0, 1, 0)
        }

        // --- ЛЕВАЯ ПАРА КНОПОК: x1, x2 ---
        val leftMultipliers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }

        settings.btnX1.apply { text = "x1"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 1; refreshAllUI() } }
        settings.btnX2.apply { text = "x2"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 2; refreshAllUI() } }

        (settings.btnX1.parent as? LinearLayout)?.removeView(settings.btnX1)
        (settings.btnX2.parent as? LinearLayout)?.removeView(settings.btnX2)

        leftMultipliers.addView(settings.btnX1)
        leftMultipliers.addView(settings.btnX2)
        container.addView(leftMultipliers)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // --- ЦЕНТРАЛЬНЫЙ БЛОК: Текстовые строки состояния (Громкость, порог, частоты) ---
        val centerTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.52f)
        }

        statusLine1 = TextView(this).apply {
            text = "Ожидание запуска"
            textSize = 11f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine1)

        statusLine2 = TextView(this).apply {
            text = "Громк: 0 | Пор: 20"
            textSize = 10f
            setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine2)

        statusLine3 = TextView(this).apply {
            text = "Pre: 0 Гц | Others: 0 Гц"
            textSize = 10f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        centerTextCol.addView(statusLine3)

        container.addView(centerTextCol)

        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })

        // --- ПРАВАЯ ПАРА КНОПОК: x3, x4 ---
        val rightMultipliers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, panelHeight, 0.22f)
        }

        settings.btnX3.apply { text = "x3"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 3; refreshAllUI() } }
        settings.btnX4.apply { text = "x4"; textSize = 10f; setPadding(0, 0, 0, 0); layoutParams = btnParams; setOnClickListener { currentMultiplier = 4; refreshAllUI() } }

        (settings.btnX3.parent as? LinearLayout)?.removeView(settings.btnX3)
        (settings.btnX4.parent as? LinearLayout)?.removeView(settings.btnX4)

        rightMultipliers.addView(settings.btnX3)
        rightMultipliers.addView(settings.btnX4)
        container.addView(rightMultipliers)

        return container
    }
    
