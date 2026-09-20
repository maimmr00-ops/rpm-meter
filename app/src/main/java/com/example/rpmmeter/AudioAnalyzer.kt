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
    private val onUpdate: (rpm: Int, rawFreq: Float, filteredFreq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null

    private var smoothedVolume = 0f
    
    // Память для кадров плавности 4T (2, 5, 8 кадров)
    private val history4T = FloatArray(8) { 0f }
    private var historyIndex4T = 0

    // Память для 2T
    private var lastValidLag = -1
    private var lastValidRpm = 0f

    // Память для Others
    private var smoothedOtherFreq = 0f

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

                // 1. Расчет громкости
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
                    lastValidLag = -1
                    lastValidRpm = 0f
                    smoothedOtherFreq = 0f
                    java.util.Arrays.fill(history4T, 0f)
                    onUpdate(0, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                val smoothPreset = prefsManager.smoothPreset
                val engineType = prefsManager.engineType

                // 2. Выбор алгоритма
                val rawFreq = when (engineType) {
                    4 -> findFrequencyZeroCrossing(buffer, readCount, sampleRate)
                    2 -> findFrequencyAutocorrelation(buffer, readCount, sampleRate)
                    else -> findFrequencySpectralPeak(buffer, readCount, sampleRate)
                }

                if (rawFreq < 10.0f || rawFreq > 400.0f) {
                    lastValidLag = -1
                    lastValidRpm = 0f
                    smoothedOtherFreq = 0f
                    onUpdate(0, rawFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                // 3. Базовый расчет RPM
                val instantRpm = when (engineType) {
                    2 -> rawFreq * 60.0f
                    4 -> rawFreq * 30.0f
                    else -> rawFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (instantRpm > maxAllowed) continue

                // 4. Честная работа пресетов плавности (Sharp = 2 кадра, Norm = 5 кадров, Soft = 8 кадров)
                val finalRpm = when (engineType) {
                    4 -> {
                        history4T[historyIndex4T] = instantRpm
                        historyIndex4T = (historyIndex4T + 1) % 8
                        val frames = when (smoothPreset) { 0 -> 2; 1 -> 5; else -> 8 }
                        var s = 0f
                        for (i in 0 until frames) {
                            s += history4T[(historyIndex4T - 1 - i + 8) % 8]
                        }
                        s / frames
                    }
                    2 -> {
                        var corrected = instantRpm
                        if (lastValidRpm > 0f && corrected > lastValidRpm + 400f) {
                            corrected = lastValidRpm // Давим галлюцинацию вверх
                        }

                        val steps = when (smoothPreset) {
                            0 -> 1.0f // Sharp: мгновенно
                            1 -> 5.0f // Norm: 5 шагов
                            else -> 8.0f // Soft: 8 шагов
                        }

                        val res = if (smoothPreset == 0) {
                            corrected
                        } else {
                            // Плавность работает и на рост, и на падение
                            lastValidRpm + (corrected - lastValidRpm) / steps
                        }
                        lastValidRpm = res
                        res
                    }
                    else -> {
                        val alpha = when (smoothPreset) {
                            0 -> 1.0f  // Sharp
                            1 -> 0.25f // Norm
                            else -> 0.12f // Soft
                        }
                        if (smoothedOtherFreq == 0f || smoothPreset == 0) {
                            smoothedOtherFreq = instantRpm
                        } else {
                            smoothedOtherFreq = smoothedOtherFreq + alpha * (instantRpm - smoothedOtherFreq)
                        }
                        smoothedOtherFreq
                    }
                }

                onUpdate(finalRpm.toInt(), rawFreq, finalRpm, currentVolInt, "Работа мотора")
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

        val minLag: Int
        val maxLag: Int

        // Исправленный коридор: при разгоне лаг уменьшается (идем в сторону 0.5f), при сбросе растет (до 1.5f)
        if (lastValidLag > 0) {
            minLag = (lastValidLag * 0.5f).toInt().coerceAtLeast(absoluteMinLag)
            maxLag = (lastValidLag * 1.5f).toInt().coerceAtMost(absoluteMaxLag)
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

        if (bestLag <= 0) {
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

        lastValidLag = bestLag
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
            freq += 1.5f
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
