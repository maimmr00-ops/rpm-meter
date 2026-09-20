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
            val sampleRate = 8000 // Оптимальная частота для анализа моторов
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

                // 1. Расчет громкости
                var sum = 0.0
                val limitSamples = minOf(readCount, 512)
                for (i in 0 until limitSamples) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / limitSamples)
                
                smoothedVolume = smoothedVolume + 0.3f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                val minThreshold = prefsManager.minVolumeThreshold
                if (currentVolInt < minThreshold) {
                    onUpdate(0, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                // 2. Выбор метода анализа в зависимости от типа двигателя
                val engineType = prefsManager.engineType
                val rawFreq = if (engineType == 4) {
                    findFrequencyZeroCrossing(buffer, limitSamples, sampleRate)
                } else {
                    findFrequencyAutocorrelation(buffer, limitSamples, sampleRate)
                }

                if (rawFreq < 10.0f || rawFreq > 400.0f) {
                    onUpdate(0, rawFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                // 3. Расчет RPM
                val calculatedRpm = when (engineType) {
                    2 -> rawFreq * 60.0f
                    4 -> rawFreq * 30.0f // 4T: 1 вспышка на 2 оборота
                    else -> rawFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (calculatedRpm > maxAllowed) {
                    continue
                }

                                // 4. Плавность с честной реакцией на выбранный пресет кнопок
                val riseAlpha = prefsManager.riseTimeConstant 
                val fallAlpha = prefsManager.fallTimeConstant // Теперь здесь правильные значения из Sharp/Norm/Soft

                // Если расчетные обороты ниже текущих сглаженных — используем коэффициент падения (fall)
                // Если выше — коэффициент роста (rise)
                val alpha = if (calculatedRpm >= smoothedRpm) {
                    riseAlpha
                } else {
                    // Делаем падение чуть динамичнее для 4T, но строго подчиненным кнопке плавности
                    fallAlpha * if (engineType == 4) 1.5f else 1.0f
                }
                
                smoothedRpm = smoothedRpm + alpha * (calculatedRpm - smoothedRpm)

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
