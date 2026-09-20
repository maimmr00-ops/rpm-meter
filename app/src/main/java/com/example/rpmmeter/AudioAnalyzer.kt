package com.example.rpmmeter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rpm: Int, rawFreq: Float, filteredFreq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null

    private var smoothedRpm = 0f
    private var smoothedVolume = 0f

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true

        analysisThread = Thread {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, prefsManager.audioBufferSize)
            
            val buffer = ShortArray(bufferSize)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Ошибка инициализации микрофона")
                isRunning = false
                return@Thread
            }

            try {
                audioRecord?.startRecording()
            } catch (e: Exception) {
                onError("Ошибка запуска записи: ${e.localizedMessage}")
                isRunning = false
                return@Thread
            }

            while (isRunning) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount <= 0) continue

                // 1. Расчет громкости (RMS)
                var sum = 0.0
                for (i in 0 until readCount) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / readCount)
                
                smoothedVolume = smoothedVolume + 0.2f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                val minThreshold = prefsManager.minVolumeThreshold
                if (currentVolInt < minThreshold) {
                    onUpdate(0, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // 2. Автокорреляционный анализ частоты
                val rawFreq = findFrequencyAutocorrelation(buffer, readCount, sampleRate)

                if (rawFreq < 15.0f || rawFreq > 1000.0f) {
                    onUpdate(0, rawFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                // 3. Вычисление оборотов (для 4T корректный расчет через деление/множение частоты)
                val engineType = prefsManager.engineType
                val calculatedRpm = when (engineType) {
                    2 -> rawFreq * 60.0f  // 2T: 1 вспышка на оборот
                    4 -> rawFreq * 30.0f  // 4T: вспышка каждые 2 оборота (реальная частота коленвала вдвое выше частоты вспышек)
                    else -> rawFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (calculatedRpm > maxAllowed) {
                    continue
                }

                // 4. Честное сглаживание RPM из настроек без жестких костылей
                val riseAlpha = prefsManager.riseTimeConstant
                val fallAlpha = prefsManager.fallTimeConstant

                val alpha = if (calculatedRpm > smoothedRpm) riseAlpha else fallAlpha
                smoothedRpm = smoothedRpm + alpha * (calculatedRpm - smoothedRpm)

                val finalRpm = smoothedRpm.toInt()
                onUpdate(finalRpm, rawFreq, calculatedRpm, currentVolInt, "Работа мотора")
            }
        }
        analysisThread?.start()
    }

    private fun findFrequencyAutocorrelation(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        val minLag = sampleRate / 1000
        val maxLag = sampleRate / 15

        if (size <= maxLag) return 0f

        var bestLag = -1
        var maxCorrelation = -1.0

        for (lag in minLag..maxLag) {
            var correlation = 0.0
            val limit = size - lag
            for (i in 0 until limit) {
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
