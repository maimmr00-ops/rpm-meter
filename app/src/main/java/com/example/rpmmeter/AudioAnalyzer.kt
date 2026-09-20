package com.example.rpmmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rpm: Int, frequency: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var analysisThread: Thread? = null

    private var smoothedRpm = 0f
    private var smoothedVolume = 0f

    fun start() {
        if (isRunning) return
        isRunning = true

        analysisThread = Thread {
            runAnalysis()
        }
        analysisThread?.start()
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
        val sampleRate = 8000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        var bufferSize = prefsManager.audioBufferSize
        var minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize < minBuf) bufferSize = minBuf * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: SecurityException) {
            onError("Нет разрешения на запись аудио!")
            return
        } catch (e: Exception) {
            onError("Ошибка инициализации микрофона: ${e.message}")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("Микрофон не инициализирован!")
            return
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            onError("Не удалось запустить запись: ${e.message}")
            return
        }

        val shortBuffer = ShortArray(bufferSize)

        while (isRunning) {
            val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
            if (readCount <= 0) continue

            // 1. Вычисление громкости (RMS)
            var sum = 0.0
            for (i in 0 until readCount) {
                val v = shortBuffer[i].toDouble()
                sum += v * v
            }
            val rawVolume = sqrt(sum / readCount).toInt()

            // Сглаживание громкости
            smoothedVolume = smoothedVolume * 0.7f + rawVolume * 0.3f
            val currentVolume = smoothedVolume.toInt()

            // 2. Отсечка по порогу (шумодав квадратиков VU-метра)
            val threshold = prefsManager.minVolumeThreshold
            if (currentVolume < threshold) {
                smoothedRpm = 0f
                onUpdate(0, 0f, currentVolume, "Ожидание (тихо)...")
                continue
            }

            val engineType = prefsManager.engineType
            var frequency = 0f
            var rawRpm = 0

            if (engineType == 3) {
                // РЕЖИМ OTHERS ("ОЗЕРО"): считает всё подряд, всю частоту без фильтрации пиков
                var zeroCrossings = 0
                for (i in 1 until readCount) {
                    if ((shortBuffer[i - 1] < 0 && shortBuffer[i] >= 0) || 
                        (shortBuffer[i - 1] >= 0 && shortBuffer[i] < 0)) {
                        zeroCrossings++
                    }
                }
                frequency = (zeroCrossings.toFloat() * sampleRate) / (2.0f * readCount)
                rawRpm = (frequency * 60f).toInt()
            } else {
                // РЕЖИМЫ 2T и 4T: Ищет именно мощные ПИКИ выхлопа, отсекая мелкие гармоники и шум
                var peakCount = 0
                
                // Динамический порог амплитуды для поиска основных пиков внутри буфера
                var maxAmplitude = 0
                for (i in 0 until readCount) {
                    val absVal = abs(shortBuffer[i].toInt())
                    if (absVal > maxAmplitude) maxAmplitude = absVal
                }
                
                // Пиком считаем всплеск, достигающий хотя бы 40% от максимального в текущем буфере
                val peakThreshold = (maxAmplitude * 0.4f).toInt().coerceAtLeast(100)
                
                // Минимальное расстояние между пиками (в отсчетах), чтобы избежать дребезга гармоник
                // Ограничивает максимальный фиксируемый RPM разумными пределами
                val minSamplesBetweenPeaks = 15 
                var lastPeakIndex = -999

                for (i in 1 until (readCount - 1)) {
                    val prev = shortBuffer[i - 1].toInt()
                    val curr = shortBuffer[i].toInt()
                    val next = shortBuffer[i + 1].toInt()

                    // Проверяем локальный максимум, превышающий порог пика выхлопа
                    if (curr > prev && curr >= next && curr > peakThreshold) {
                        if ((i - lastPeakIndex) >= minSamplesBetweenPeaks) {
                            peakCount++
                            lastPeakIndex = i
                        }
                    }
                }

                // Переводим количество найденных пиков за время буфера в частоту вспышек в секунду (Гц)
                val durationSec = readCount.toFloat() / sampleRate
                frequency = if (durationSec > 0f) peakCount / durationSec else 0f

                // Коррекция под такты мотора:
                // 2T: 1 вспышка на 1 оборот -> умножаем на 60
                // 4T: 1 вспышка на 2 оборота -> умножаем на 120
                rawRpm = when (engineType) {
                    2 -> (frequency * 60f).toInt()
                    4 -> (frequency * 120f).toInt()
                    else -> (frequency * 60f).toInt()
                }
            }

            val maxLimit = prefsManager.maxAllowedRpm
            val clampedRpm = rawRpm.coerceIn(0, maxLimit)

            // Сглаживание рывков RPM (Rise / Fall)
            val rise = prefsManager.riseTimeConstant
            val fall = prefsManager.fallTimeConstant
            val alpha = if (clampedRpm > smoothedRpm) rise else fall
            smoothedRpm = smoothedRpm + alpha * (clampedRpm - smoothedRpm)

            val finalRpm = smoothedRpm.toInt()
            val statusMsg = when (engineType) {
                2 -> "Работает (2T - Пики)"
                4 -> "Работает (4T - Пики)"
                else -> "Работает (Others - Озеро)"
            }

            onUpdate(finalRpm, frequency, currentVolume, statusMsg)
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
