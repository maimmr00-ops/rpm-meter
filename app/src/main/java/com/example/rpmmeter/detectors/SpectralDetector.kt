package com.example.rpmmeter.detectors

import kotlin.math.cos
import kotlin.math.sin

class SpectralDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minFreq = 20.0f
        val maxFreq = 400.0f
        var bestFreq = 0f
        var maxPower = 0.0

        var freq = minFreq
        while (freq <= maxFreq) {
            var real = 0.0
            var imag = 0.0
            val limit = size.coerceAtMost(256)
            var i = 0
            while (i < limit) {
                val angle = 2.0 * Math.PI * freq * i / sampleRate
                val sampleVal = buffer[i].toDouble()
                real += sampleVal * cos(angle)
                imag += sampleVal * sin(angle)
                i += 2
            }
            val power = real * real + imag * imag
            if (power > maxPower) {
                maxPower = power
                bestFreq = freq
            }
            freq += 2.0f
        }
        return bestFreq
    }
}
