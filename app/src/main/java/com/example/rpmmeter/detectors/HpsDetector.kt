package com.example.rpmmeter.detectors

import kotlin.math.cos
import kotlin.math.sin

class HpsDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val maxBin = 100
        val spectrum = FloatArray(maxBin)
        
        // Упрощенный спектр по гармоникам
        for (k in 1 until maxBin) {
            var real = 0.0
            var imag = 0.0
            val limit = size.coerceAtMost(128)
            for (i in 0 until limit step 2) {
                val angle = 2.0 * Math.PI * k * i / limit
                real += buffer[i] * cos(angle)
                imag += buffer[i] * sin(angle)
            }
            spectrum[k] = (real * real + imag * imag).toFloat()
        }
        
        // Перемножение спектров (HPS с фактором 2)
        var maxVal = -1f
        var bestBin = 1
        val limitHps = maxBin / 2
        for (k in 2 until limitHps) {
            val prod = spectrum[k] * spectrum[2 * k]
            if (prod > maxVal) {
                maxVal = prod
                bestBin = k
            }
        }
        
        if (bestBin <= 0) return 0f
        return (bestBin.toFloat() * sampleRate) / size.toFloat()
    }
}
