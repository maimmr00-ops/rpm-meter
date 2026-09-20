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
    private var lastValidLag = -1 // Память последнего успешного лага для стабильности 2T

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
                    lastValidLag = -1 // Сбрасываем память лага при тишине
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
                    lastValidLag = -1
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

                // 4. ИНДИВИДУАЛЬНЫЙ АЛГОРИТМ ПЛАВНОСТИ (Sharp = 1, Norm = 2, Soft = 3)
                val smoothPreset = prefsManager.smoothPreset

                when (smoothPreset) {
                    0 -> {
                        // --- SHARP: 1 кадр (мгновенный отклик) ---
                        smoothedRpm = calculatedRpm
                        decayCounter = 0
                    }
                    1 -> {
                        // --- NORM: Ровно 2 кадра на спад ---
                        if (calculatedRpm >= smoothedRpm) {
                            smoothedRpm = calculatedRpm
                            decayCounter = 0
                        } else {
                            val diff = smoothedRpm - calculatedRpm
                            smoothedRpm -= (diff / 2.0f).coerceAtLeast(1.0f)
                            if (smoothedRpm < calculatedRpm) smoothedRpm = calculatedRpm
                        }
                    }
                    else -> {
                        // --- SOFT: Ровно 3 кадра с микро-удержанием ---
                        if (calculatedRpm >= smoothedRpm) {
                            smoothedRpm = smoothedRpm + 0.6f * (calculatedRpm - smoothedRpm)
                            decayCounter = 1 
                        } else {
                            if (decayCounter > 0) {
                                decayCounter-- 
                            } else {
                                val diff = smoothedRpm - calculatedRpm
                                smoothedRpm -= (diff / 3.0f).coerceAtLeast(1.0f)
                                if (smoothedRpm < calculatedRpm) smoothedRpm = calculatedRpm
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
        val absoluteMinLag = sampleRate / 300 // Максимум 300 Гц (~18000 RPM)
        val absoluteMaxLag = sampleRate / 20  // Минимум 20 Гц (~1200 RPM)

        if (size <= absoluteMaxLag) return 0f

        // Узкий скользящий коридор вокруг предыдущего успешного значения (±35%)
        // Это полностью блокирует скачки автокорреляции по ложным гармоникам при сбросе газа
        val minLag: Int
        val maxLag: Int

        if (lastValidLag > 0) {
            minLag = (lastValidLag * 0.65f).toInt().coerceAtLeast(absoluteMinLag)
            maxLag = (lastValidLag * 1.35f).toInt().coerceAtMost(absoluteMaxLag)
        } else {
            minLag = absoluteMinLag
            maxLag = absoluteMaxLag
        }

        var bestLag = -1
        var maxCorrelation = -1.0

        // Первый проход: ищем внутри узкого защитного коридора
        for (lag in minLag..maxLag step 1) {
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

        // Если в узком коридоре сигнал потерялся, делаем один полный поиск для подстраховки
        if (bestLag <= 0 && lastValidLag > 0) {
            bestLag = -1
            maxCorrelation = -1.0
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
        }

        if (bestLag <= 0) {
            lastValidLag = -1
            return 0f
        }

        lastValidLag = bestLag
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
