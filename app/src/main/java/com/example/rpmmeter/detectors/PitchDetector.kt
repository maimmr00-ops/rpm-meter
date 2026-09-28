package com.example.rpmmeter.detectors

interface PitchDetector {
    fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float
}
