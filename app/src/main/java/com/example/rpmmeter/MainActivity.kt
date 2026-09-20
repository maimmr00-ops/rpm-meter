private fun buildTopPanel(): View {
    // Главный горизонтальный контейнер верхней панели
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 12, 0, 4)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Левый пустой вес (для центрирования всей конструкции на экране)
    container.addView(View(this).apply { 
        layoutParams = LinearLayout.LayoutParams(0, 1, 0.05f) 
    })

    // Центральный блок для цифр и метки RPM (выравнивание по правому краю)
    val rpmContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f)
    }

    // Цифры оборотов (огромные, выровнены по правому краю, чтобы не скакали)
    rpmText = TextView(this).apply {
        text = "0"
        textSize = 76f
        setTextColor(Color.parseColor("#00E676"))
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        // Включаем моноширинные/цифровые отступы, если поддерживает система, 
        // либо просто выравнивание по правому краю за счет веса
        includeFontPadding = false
    }
    
    // Обертка для цифр с фиксированным/гибким весом, чтобы они прижимались вправо
    val textWrapper = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    textWrapper.addView(rpmText)
    rpmContainer.addView(textWrapper)

    // Метка RPM — переносим её в правую часть, сразу за цифрами, делаем крупнее
    val rpmLabel = TextView(this).apply {
        text = " RPM"
        textSize = 22f
        setTextColor(Color.parseColor("#80CBC4"))
        gravity = Gravity.BOTTOM or Gravity.START
        setPadding(4, 0, 0, 16) // Чуть сдвигаем вниз, чтобы красиво смотрелось рядом с большими цифрами
        includeFontPadding = false
    }
    rpmContainer.addView(rpmLabel)

    container.addView(rpmContainer)

    // Правая колонка для кнопки HOLD (оставляем компактной сбоку)
    val rightCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, 120, 0.2f)
    }

    btnHold = Button(this).apply {
        text = "HOLD"
        textSize = 11f
        setOnClickListener {
            isHoldActive = !isHoldActive
            if (isHoldActive) heldRpmValue = currentRealRpm
            refreshAllUI()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT
        ).apply {
            setMargins(8, 0, 0, 0)
        }
    }
    rightCol.addView(btnHold)
    container.addView(rightCol)

    return container
}
