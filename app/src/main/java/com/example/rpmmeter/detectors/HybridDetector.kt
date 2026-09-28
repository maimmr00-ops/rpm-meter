package com.example.rpmmeter.detectors

class HybridDetector : PitchDetector {
    private val fallback = AutocorrelationDetector()
    
    override fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        // Здесь в дальнейшем можно будет скомбинировать Spectral и AutoCorr
        return fallback.detect(buffer, size, sampleRate)
    }
}
