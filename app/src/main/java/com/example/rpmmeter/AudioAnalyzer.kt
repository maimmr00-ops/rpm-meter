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
    private var decayCounter = 0

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
                if (!isRunning) break

                val readCount = try {
                    record.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    -1
                }

                if (readCount <= 0) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    continue
                }

                // 1. Расчет громкости по ВСЕМУ прочитанному буферу
                var sum = 0.0
                for (i in 0 until readCount) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / readCount)
                
                smoothedVolume = smoothedVolume + 0.3f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                val minThreshold = prefsManager.minVolumeThreshold
                if (currentVolInt < minThreshold) {
                    smoothedRpm *= 0.3f
                    if (smoothedRpm < 100f) smoothedRpm = 0f
                    onUpdate(smoothedRpm.toInt(), 0f, smoothedRpm, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // 2. Выбор метода анализа в зависимости от типа двигателя
                val engineType = prefsManager.engineType
                val rawFreq = if (engineType == 4) {
                    findFrequencyZeroCrossing(buffer, readCount, sampleRate)
                } else {
                    findFrequencyAutocorrelation(buffer, readCount, sampleRate)
                }

                if (rawFreq < 10.0f || rawFreq > 400.0f) {
                    smoothedRpm *= 0.3f
                    onUpdate(smoothedRpm.toInt(), rawFreq, smoothedRpm, currentVolInt, "Поиск сигнала...")
                    continue
                }

                // 3. Расчет RPM
                val calculatedRpm = when (engineType) {
                    2 -> rawFreq * 60.0f
                    4 -> rawFreq * 30.0f
                    else -> rawFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (calculatedRpm > maxAllowed) {
                    continue
                }

                // 4. ИНДИВИДУАЛЬНЫЙ АЛГОРИТМ ДЛЯ КАЖДОЙ КНОПКИ ПЛАВНОСТИ
                val smoothPreset = prefsManager.smoothPreset

                when (smoothPreset) {
                    0 -> {
                        // --- SHARP: Чистый «сырой» сигнал (1 кадр) ---
                        // Никакого сглаживания, максимальная отзывчивость
                        smoothedRpm = calculatedRpm
                        decayCounter = 0
                    }
                    1 -> {
                        // --- NORM: Сбалансированный режим (строго 2 кадра на спад) ---
                        if (calculatedRpm >= smoothedRpm) {
                            // Вверх идем сразу
                            smoothedRpm = calculatedRpm
                            decayCounter = 0
                        } else {
                            // Вниз — делим оставшуюся дистанцию ровно пополам за 2 кадра
                            smoothedRpm -= (smoothedRpm - calculatedRpm) / 2.0f
                            if (smoothedRpm < calculatedRpm + 5f) smoothedRpm = calculatedRpm
                        }
                    }
                    else -> {
                        // --- SOFT: Мягкий режим с микро-удержанием (строго 3 кадра) ---
                        if (calculatedRpm >= smoothedRpm) {
                            smoothedRpm = smoothedRpm + 0.8f * (calculatedRpm - smoothedRpm)
                            decayCounter = 1 // Небольшое удержание на 1 такт при смене тренда
                        } else {
                            if (decayCounter > 0) {
                                decayCounter-- // Стоим на месте 1 кадр, чтобы сгладить пик
                            } else {
                                // Падаем ровно за 3 шага
                                smoothedRpm -= (smoothedRpm - calculatedRpm) / 3.0f
                                if (smoothedRpm < calculatedRpm + 5f) smoothedRpm = calculatedRpm
                            }
                        }
                    }
                }



                onUpdate(smoothedRpm.toInt(), rawFreq, smoothedRpm, currentVolInt, "Работа мотора")
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
        val minLag = sampleRate / 300
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
