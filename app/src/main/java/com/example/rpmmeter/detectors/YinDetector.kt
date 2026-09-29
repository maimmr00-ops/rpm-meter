package com.example.rpmmeter.detectors

class YinDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val halfSize = size / 2
        if (halfSize < 2) return 0f
        
        val yinBuffer = FloatArray(halfSize)
        
        // Шаг 1: Разность (Difference function)
        for (tau in 0 until halfSize) {
            var sum = 0f
            for (i in 0 until halfSize) {
                val delta = buffer[i] - buffer[i + tau]
                sum += delta * delta
            }
            yinBuffer[tau] = sum
        }
        
        // Шаг 2: Кумулятивное среднее нормирование (Cumulative mean normalized difference)
        yinBuffer[0] = 1f
        var runningSum = 0f
        for (tau in 1 until halfSize) {
            runningSum += yinBuffer[tau]
            if (runningSum == 0f) {
                yinBuffer[tau] = 1f
            } else {
                yinBuffer[tau] = yinBuffer[tau] * tau / runningSum
            }
        }
        
        // Шаг 3: Поиск порога (Absolute threshold)
        val threshold = 0.15f
        var tau = 2
        while (tau < halfSize) {
            if (yinBuffer[tau] < threshold) {
                while (tau + 1 < halfSize && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++
                }
                break
            }
            tau++
        }
        
        if (tau >= halfSize || yinBuffer[tau] >= threshold) {
            // Если ниже порога не нашли, ищем абсолютный минимум
            var minTau = 2
            var minVal = yinBuffer[2]
            for (i in 3 until halfSize) {
                if (yinBuffer[i] < minVal) {
                    minVal = yinBuffer[i]
                    minTau = i
                }
            }
            tau = minTau
        }
        
        if (tau <= 0 || tau >= halfSize) return 0f
        return sampleRate.toFloat() / tau.toFloat()
    }
}
