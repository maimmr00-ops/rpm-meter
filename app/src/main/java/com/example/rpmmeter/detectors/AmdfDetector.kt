package com.example.rpmmeter.detectors

import kotlin.math.abs

class AmdfDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minLag = sampleRate / 300
        val maxLag = sampleRate / 20
        if (size <= maxLag) return 0f

        var bestLag = -1
        var minAmdf = Double.MAX_VALUE

        for (lag in minLag..maxLag step 2) {
            var sum = 0.0
            val limit = size - lag
            for (i in 0 until limit step 4) {
                sum += abs(buffer[i].toDouble() - buffer[i + lag].toDouble())
            }
            if (sum < minAmdf) {
                minAmdf = sum
                bestLag = lag
            }
        }

        if (bestLag <= 0) return 0f
        return sampleRate.toFloat() / bestLag.toFloat()
    }
}
