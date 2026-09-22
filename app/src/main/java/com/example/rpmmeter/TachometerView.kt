package com.example.rpmmeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class TachometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var targetRpm = 0f
    private var displayedRpm = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    // Ссылка на менеджер настроек, чтобы читать текущий пресет плавности (0 - Sharp, 1 - Norm, 2 - Soft)
    var prefsManager: PreferencesManager? = null

    private val textPaint = Paint().apply {
        color = Color.parseColor("#00FF66") // Фирменный зеленый цвет цифр с твоего скриншота
        textSize = 140f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val labelPaint = Paint().apply {
        color = Color.GRAY
        textSize = 42f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun setTargetRpm(rpm: Float) {
        targetRpm = rpm
        if (!isAnimating) {
            startInertiaLoop()
        }
    }

    private fun startInertiaLoop() {
        isAnimating = true
        handler.post(object : Runnable {
            override fun run() {
                val diff = targetRpm - displayedRpm
                
                // Читаем выбранный пресет плавности (Sharp, Norm, Soft)
                val preset = prefsManager?.smoothPreset ?: 0
                
                val smoothingFactor = when (preset) {
                    0 -> 1.0f  // SHARP: Мгновенный отклик, без задержек
                    1 -> if (targetRpm < displayedRpm) 0.2f else 0.4f // NORM: Умеренное затухание
                    else -> if (targetRpm < displayedRpm) 0.08f else 0.25f // SOFT: Тяжелое, медленное падение
                }

                if (preset == 0) {
                    displayedRpm = targetRpm
                } else {
                    displayedRpm += diff * smoothingFactor
                }

                invalidate() // Перерисовываем циферблат

                // Продолжаем анимацию, пока значение не стабилизируется
                if (Math.abs(diff) > 0.5f || targetRpm > 0f) {
                    handler.postDelayed(this, 16L) // ~60 FPS обновление экрана
                } else {
                    isAnimating = false
                }
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        // Рисуем только крупные цифры и подпись RPM (никаких стрелок)
        canvas.drawText("${displayedRpm.toInt()}", centerX, centerY + 20f, textPaint)
        canvas.drawText("RPM (об / мин)", centerX, centerY + 90f, labelPaint)
    }
}
