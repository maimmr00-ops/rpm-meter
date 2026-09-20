package com.example.rpmmeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rpm: Int, frequency: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null

    // Сглаженные значения для плавной работы
    private var smoothedRpm = 0f
    private var smoothedFreq = 0f

    fun start() {
        if (isRunning) return
        isRunning = true

        analysisThread = Thread {
            runAnalysis()
        }.apply {
            start()
        }
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

    private fun runAnalysis() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = prefsManager.audioBufferSize.coerceAtLeast(1024)
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val actualBufferSize = maxOf(bufferSize, minBufferSize)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                actualBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Ошибка инициализации микрофона")
                return
            }

            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            onError("Нет разрешения на запись аудио")
            return
        } catch (e: Exception) {
            onError("Ошибка старта: ${e.localizedMessage}")
            return
        }

        val buffer = ShortArray(actualBufferSize)

        while (isRunning) {
            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readCount <= 0) continue

            // 1. Расчет громкости (RMS)
            var sum = 0.0
            for (i in 0 until readCount) {
                val v = buffer[i].toDouble()
                sum += v * v
            }
            val rms = sqrt(sum / readCount)
            val currentVolume = rms.toInt()

            // 2. Вычисление доминирующей частоты (простейший метод автокорреляции / пересечения нуля)
            val frequency = estimateFrequency(buffer, readCount, sampleRate)

            // 3. ПРОВЕРКА ПОРОГА ГРОМКОСТИ
            // Если реальный звук тише выбранного на шкале порога — сбрасываем обороты в 0
            if (currentVolume < prefsManager.minVolumeThreshold) {
                smoothedRpm = 0f
                val engineName = when (prefsManager.engineType) {
                    2 -> "2T"
                    4 -> "4T"
                    else -> "Others"
                }
                onUpdate(0, frequency, currentVolume, "Ожидание (тихо)...")
                continue
            }

            // 4. Расчет оборотов в зависимости от типа мотора
            val rawRpm = calculateRpmFromFrequency(frequency, prefsManager.engineType)

            // 5. Применение коэффициентов плавности
            val riseAlpha = prefsManager.riseTimeConstant
            val fallAlpha = prefsManager.fallTimeConstant

            val alpha = if (rawRpm > smoothedRpm) riseAlpha else fallAlpha
            smoothedRpm = smoothedRpm + alpha * (rawRpm - smoothedRpm)
            smoothedFreq = smoothedFreq + 0.2f * (frequency - smoothedFreq)

            val finalRpm = smoothedRpm.toInt().coerceIn(0, prefsManager.maxAllowedRpm)
            val engineLabel = when (prefsManager.engineType) {
                2 -> "2T"
                4 -> "4T"
                else -> "Others"
            }

            onUpdate(finalRpm, smoothedFreq, currentVolume, "Работает ($engineLabel)")
        }
    }

    private fun estimateFrequency(buffer: ShortArray, size: Int, sampleRate: Int): Float {
        // Метод подсчета пересечений нуля (Zero-Crossing Rate) с фильтрацией шума
        var zeroCrossings = 0
        for (i in 1 until size) {
            if ((buffer[i - 1] < 0 && buffer[i] >= 0) || (buffer[i - 1] >= 0 && buffer[i] < 0)) {
                zeroCrossings++
            }
        }
        val freq = (zeroCrossings.toFloat() * sampleRate) / (2.0f * size)
        return if (freq in 20.0..3000.0) freq else 0f
    }

    private fun calculateRpmFromFrequency(freq: Float, engineType: Int): Float {
        if (freq <= 5f) return 0f
        return when (engineType) {
            2 -> freq * 60f       // 2T: 1 вспышка на 1 оборот
            4 -> freq * 120f      // 4T: 1 вспышка на 2 оборота
            else -> freq * 60f    // Others по умолчанию как 2T
        }
    }
}
