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

    private var smoothedVolume = 0f
    
    // Внутренние переменные памяти для алгоритмов (живут прямо в сердце расчетов)
    private var smoothedLag = -1f       // Для автокорреляции (2T / Others)
    private var smoothedCrossingFreq = -1f // Для Zero-Crossing (4T)

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
                    smoothedLag = -1f
                    smoothedCrossingFreq = -1f
                    onUpdate(0, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // Передаем текущий пресет плавности (Sharp=0, Norm=1, Soft=2) прямо в алгоритмы!
                val smoothPreset = prefsManager.smoothPreset
                val engineType = prefsManager.engineType

                // 2 & 3. Расчет частоты С УЧЕТОМ ПЛАВНОСТИ ВНУТРИ АЛГОРИТМА
                val rawFreq = if (engineType == 4) {
                    findFrequencyZeroCrossing(buffer, readCount, sampleRate, smoothPreset)
                } else {
                    findFrequencyAutocorrelation(buffer, readCount, sampleRate, smoothPreset)
                }

                if (rawFreq < 10.0f || rawFreq > 400.0f) {
                    smoothedLag = -1f
                    smoothedCrossingFreq = -1f
                    onUpdate(0, rawFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                // Финальный расчет RPM из уже сглаженной алгоритмом частоты
                val calculatedRpm = when (engineType) {
                    2 -> rawFreq * 60.0f
                    4 -> rawFreq * 30.0f
                    else -> rawFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (calculatedRpm > maxAllowed) {
                    continue
                }

                // Больше не нужны костыли на выходе — алгоритмы сами выдают чистый результат нужной степени плавности!
                onUpdate(calculatedRpm.toInt(), rawFreq, calculatedRpm, currentVolInt, "Работа мотора")
            }
        }
        analysisThread?.start()
    }

    /**
     * Алгоритм Zero-Crossing для 4T с внедренной плавностью прямо в расчет
     */
    private fun findFrequencyZeroCrossing(buffer: ShortArray, size: Int, sampleRate: Int, preset: Int): Float {
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
        val instantFreq = (crossings.toFloat() / 2.0f) * (sampleRate.toFloat() / size.toFloat())

        // Плавность влияет на инерцию внутри 4T
        val alpha = when (preset) {
            0 -> 1.0f  // Sharp: мгновенно
            1 -> 0.5f  // Norm: сбалансированно
            else -> 0.25f // Soft: максимальная стабильность
        }

        if (smoothedCrossingFreq < 0f) {
            smoothedCrossingFreq = instantFreq
        } else {
            smoothedCrossingFreq = smoothedCrossingFreq + alpha * (instantFreq - smoothedCrossingFreq)
        }

        return smoothedCrossingFreq
    }

    /**
     * Алгоритм Autocorrelation для 2T/Others с внедренным коридором и плавностью периода
     */
    private fun findFrequencyAutocorrelation(buffer: ShortArray, size: Int, sampleRate: Int, preset: Int): Float {
        val absoluteMinLag = sampleRate / 300 
        val absoluteMaxLag = sampleRate / 20  

        if (size <= absoluteMaxLag) return 0f

        // Узкий защитный коридор зависит от пресета: на Soft коридор жестче, чтобы не было качелей
        val minLag: Int
        val maxLag: Int

        val corridorFactor = when (preset) {
            0 -> 0.5f  // Sharp: шире коридор, быстрее реакция на газ
            1 -> 0.35f // Norm
            else -> 0.2f // Soft: очень узкий коридор, давит любые качели
        }

        val currentLagInt = smoothedLag.toInt()
        if (currentLagInt > 0) {
            minLag = (currentLagInt * (1.0f - corridorFactor)).toInt().coerceAtLeast(absoluteMinLag)
            maxLag = (currentLagInt * (1.0f + corridorFactor)).toInt().coerceAtMost(absoluteMaxLag)
        } else {
            minLag = absoluteMinLag
            maxLag = absoluteMaxLag
        }

        var bestLag = -1
        var maxCorrelation = -1.0

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

        // Подстраховка широким поиском, если в коридоре пусто
        if (bestLag <= 0 && currentLagInt > 0) {
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

        if (bestLag <= 0) return 0f

        // Плавность изменения самого периода (лага) внутри алгоритма
        val lagAlpha = when (preset) {
            0 -> 1.0f  // Sharp: моментальный переход на новый лаг
            1 -> 0.6f  // Norm
            else -> 0.3f // Soft: плавное изменение периода
        }

        if (smoothedLag < 0f) {
            smoothedLag = bestLag.toFloat()
        } else {
            smoothedLag = smoothedLag + lagAlpha * (bestLag.toFloat() - smoothedLag)
        }

        return sampleRate.toFloat() / smoothedLag
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
