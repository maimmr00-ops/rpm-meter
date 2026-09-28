package com.example.rpmmeter.detectors

class ZeroCrossingDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        if (size < 2) return 0f
        
        var crossings = 0
        var sum = 0L
        for (i in 0 until size) sum += buffer[i]
        val avg = (sum / size).toInt()

        for (i in 0 until size - 1) {
            val curr = buffer[i] - avg
            val next = buffer[i + 1] - avg
            if ((curr <= 0 && next > 0) || (curr >= 0 && next < 0)) {
                crossings++
            }
        }
        
        if (crossings < 2) return 0f
        
        return (crossings.toFloat() / 2.0f) * (sampleRate.toFloat() / size.toFloat())
    }
}
