package com.example.rpmmeter.detectors

class CombFilterDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minLag = sampleRate / 300
        val maxLag = sampleRate / 20
        if (size <= maxLag) return 0f

        var bestLag = -1
        var maxEnergy = -1.0

        for (lag in minLag..maxLag step 2) {
            var energy = 0.0
            val limit = size - lag
            for (i in 0 until limit step 4) {
                // Гребенчатая фильтрация (сигнал минус задержанный)
                val diff = buffer[i] - buffer[i + lag]
                energy += diff * diff
            }
            // Для гребенчатого фильтра ищем минимум энергии ошибки (или инвертируем)
            val score = 1.0 / (1.0 + energy)
            if (score > maxEnergy) {
                maxEnergy = score
                bestLag = lag
            }
        }

        if (bestLag <= 0) return 0f
        return sampleRate.toFloat() / bestLag.toFloat()
    }
}
