package com.example.rpmmeter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val selectedAlgorithmIndex: Int, 
    private val onUpdate: (rpm: Int, rawFreq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null
    private var smoothedVolume = 0f
    private var lastValidLag = -1

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

            val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                onError("Ошибка инициализации микрофона")
                isRunning = false
                return@Thread
            }

            try { record.startRecording() } catch (e: Exception) {
                onError("Ошибка запуска: ${e.localizedMessage}")
                isRunning = false
                return@Thread
            }

            while (isRunning) {
                val readCount = try { record.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                if (readCount <= 0) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    continue
                }

                var sum = 0.0
                for (i in 0 until readCount) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / readCount)
                smoothedVolume = smoothedVolume + 0.3f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                if (currentVolInt < prefsManager.minVolumeThreshold) {
                    lastValidLag = -1
                    onUpdate(0, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // Выбор алгоритма в зависимости от индекса выбранной кнопки в новой строке
                val rawFreq = when (selectedAlgorithmIndex) {
                    0 -> findFrequencyAMDF(buffer, readCount, sampleRate)          // Кнопка 1: AMDF
                    1 -> findFrequencyZeroCrossing(buffer, readCount, sampleRate) // Кнопка 2: Zero-Crossing
                    2 -> findFrequencyAutocorrelation(buffer, readCount, sampleRate)// Кнопка 3: Autocorr
                    else -> findFrequencySpectral(buffer, readCount, sampleRate)   // Кнопка 4: Spectral
                }

                if (rawFreq < 10.0f || rawFreq > 400.0f) {
                    lastValidLag = -1
                    onUpdate(0, rawFreq, currentVolInt, "Поиск сигнала...")
                    continue
                }

                val instantRpm = when (prefsManager.engineType) {
                    2 -> rawFreq * 60.0f
                    4 -> rawFreq * 30.0f
                    else -> rawFreq * 60.0f
                }

                if (instantRpm > prefsManager.maxAllowedRpm.toFloat()) continue

                onUpdate(instantRpm.toInt(), rawFreq, currentVolInt, "Работа мотора")
            }
        }
        analysisThread?.start()
    }

    private fun findFrequencyAMDF(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minLag = sampleRate / 400
        val maxLag = sampleRate / 20
        if (size <= maxLag) return 0f

        val searchMin = if (lastValidLag > 0) (lastValidLag * 0.7f).toInt().coerceAtLeast(minLag) else minLag
        val searchMax = if (lastValidLag > 0) (lastValidLag * 1.3f).toInt().coerceAtMost(maxLag) else maxLag

        var bestLag = -1
        var minDiff = Double.MAX_VALUE

        for (lag in searchMin..searchMax step 2) {
            var diffSum = 0.0
            val limit = size - lag
            for (i in 0 until limit step 2) {
                diffSum += abs(buffer[i].toInt() - buffer[i + lag].toInt())
            }
            val avgDiff = diffSum / (limit / 2)
            if (avgDiff < minDiff) {
                minDiff = avgDiff
                bestLag = lag
            }
        }
        if (bestLag <= 0) {
            lastValidLag = -1
            return 0f
        }
        lastValidLag = bestLag
        return sampleRate.toFloat() / bestLag.toFloat()
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
        val minLag = sampleRate / 400
        val maxLag = sampleRate / 20
        if (size <= maxLag) return 0f

        var bestLag = -1
        var maxCorrelation = -1.0

        for (lag in minLag..maxLag step 2) {
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

    private fun findFrequencySpectral(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minFreq = 20.0f
        val maxFreq = 400.0f
        var bestFreq = 0f
        var maxPower = 0.0

        var freq = minFreq
        while (freq <= maxFreq) {
            var real = 0.0
            var imag = 0.0
            val limit = size.coerceAtMost(256)
            for (i in 0 until limit step 2) {
                val angle = 2.0 * Math.PI * freq * i / sampleRate
                val sampleVal = buffer[i].toDouble()
                real += sampleVal * cos(angle)
                imag += sampleVal * sin(angle)
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
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        analysisThread?.interrupt()
    }
}
