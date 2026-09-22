package com.example.rpmmeter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rawRpm: Float, allFreq: Float, preFreq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null
    private var smoothedVolume = 0f

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true

        analysisThread = Thread {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, prefsManager.audioBufferSize)
            val buffer = ShortArray(bufferSize)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                onError("Ошибка инициализации микрофона")
                isRunning = false
                return@Thread
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                onError("Ошибка запуска записи: ${e.localizedMessage}")
                isRunning = false
                return@Thread
            }

            while (isRunning) {
                val readCount = try {
                    record.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    -1
                }

                if (readCount <= 0) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    continue
                }

                // 1. Расчет громкости
                var sum = 0.0
                for (i in 0 until readCount) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / readCount)
                smoothedVolume = smoothedVolume + 0.3f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                if (currentVolInt < prefsManager.minVolumeThreshold) {
                    onUpdate(0f, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // 2. Расчет базовой сырой частоты буфера (All)
                val allFreq = findFrequencyZeroCrossing(buffer, readCount, sampleRate)

                // 3. Выбор алгоритма согласно настройке пользователя
                val preFreq = when (prefsManager.algorithmIndex) {
                    0 -> findFrequencyZeroCrossing(buffer, readCount, sampleRate) // Zero-X
                    1 -> findFrequencyAutocorrelation(buffer, readCount, sampleRate) // AutoCorr
                    2 -> findFrequencySpectralPeak(buffer, readCount, sampleRate) // Spectral
                    else -> findFrequencyAutocorrelation(buffer, readCount, sampleRate) // Hybrid
                }

                if (preFreq < 10.0f || preFreq > 400.0f) {
                    onUpdate(0f, allFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                val engineType = prefsManager.engineType
                val rawRpm = when (engineType) {
                    2 -> preFreq * 60.0f
                    4 -> preFreq * 30.0f
                    else -> preFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (rawRpm > maxAllowed) continue

                onUpdate(rawRpm, allFreq, preFreq, currentVolInt, "Работа мотора")
            }
        }
        analysisThread?.start()
    }

    private fun findFrequencyZeroCrossing(buffer: ShortArray, size: Int, sampleRate: Int): Float {
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
        return (crossings.toFloat() / 2.0f) * (sampleRate.toFloat() / size.toFloat())
    }

    private fun findFrequencyAutocorrelation(buffer: ShortArray, size: Int, sampleRate: Int): Float {
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

    private fun findFrequencySpectralPeak(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minFreq = 20.0f
        val maxFreq = 400.0f
        var bestFreq = 0f
        var maxPower = 0.0

        var freq = minFreq
        while (freq <= maxFreq) {
            var real = 0.0
            var imag = 0.0
            val limit = size.coerceAtMost(256)
            // Исправлен шаг цикла (обычный шаг +2)
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

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        analysisThread?.interrupt()
    }
}
