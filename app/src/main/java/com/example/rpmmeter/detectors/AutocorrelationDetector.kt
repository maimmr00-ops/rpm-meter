package com.example.rpmmeter.detectors

class AutocorrelationDetector : PitchDetector {
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val absoluteMinLag = sampleRate / 300
        val absoluteMaxLag = sampleRate / 20
        if (size <= absoluteMaxLag) return 0f

        var bestLag = -1
        var maxCorrelation = -1.0

        for (lag in absoluteMinLag..absoluteMaxLag step 2) {
            var correlation = 0.0
            val limit = size - lag
            for (i in 0 until limit step 4) {
                correlation += (buffer[i].toDouble() * buffer[i + lag].toDouble())
            }
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestLag = lag
            }
        }

        if (bestLag <= 0) return 0f
        return sampleRate.toFloat() / bestLag.toFloat()
    }
}
