package com.example.rpmmeter.detectors

import kotlin.math.abs

class ZeroCrossingDetector : PitchDetector {
    
    // Сохраняем последнее валидное значение для фильтрации резких провалов/скачков
    private var lastValidFreq = 0f

    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        if (size < 2) return 0f
        
        // 1. Вычисление среднего значения (смещение постоянной составляющей)
        var sum = 0L
        for (i in 0 until size) sum += buffer[i]
        val avg = (sum / size).toInt()

        // 2. Расчет максимальной амплитуды в текущем буфере (для оценки громкости/сигнала)
        var maxAmplitude = 0
        for (i in 0 until size) {
            val amp = abs(buffer[i] - avg)
            if (amp > maxAmplitude) maxAmplitude = amp
        }
        
        // Если сигнал слишком тихий (фоновый шум / тишина), сбрасываем
        // Порог 150 можно调整 под особенности микрофона устройства
        if (maxAmplitude < 150) {
            lastValidFreq = 0f
            return 0f
        }

        // 3. Гистерезис: пересечение засчитывается только если волна вышла 
        // за пределы зоны шума (например, 10-15% от максимальной амплитуды кадра)
        val hysteresis = (maxAmplitude * 0.12f).toInt().coerceAtLeast(15)

        var crossings = 0
        var above: Boolean? = null // null означает, что мы еще не определились с зоной

        for (i in 0 until size) {
            val v = buffer[i] - avg
            if (above != true && v > hysteresis) {
                crossings++
                above = true
            } else if (above != false && v < -hysteresis) {
                crossings++
                above = false
            }
        }

        if (crossings < 2) return 0f
        
        // Сырая частота по пересечениям нуля
        val rawFreq = (crossings.toFloat() / 2.0f) * (sampleRate.toFloat() / size.toFloat())

        // 4. Защита от резких «октавных» провалов и скачков (защита от самого себя)
        if (lastValidFreq > 0f) {
            val ratio = rawFreq / lastValidFreq
            // Двигатель не может мгновенно изменить обороты более чем на 50% за один микро-буфер.
            // Если частота резко рухнула в 2 раза (0.5) или подскочила — это срыв алгоритма на гармонику.
            if (ratio < 0.55f || ratio > 1.8f) {
                // Возвращаем последнее стабильное значение, сглаживая провал
                return lastValidFreq
            }
        }

        lastValidFreq = rawFreq
        return rawFreq
    }
}
