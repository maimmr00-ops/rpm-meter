package com.example.rpmmeter

interface PitchDetector {
    fun detect(buffer: ShortArray, size: Int, sampleRate: Int): Float
}
