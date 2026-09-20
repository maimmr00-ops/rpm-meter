package com.example.rpmmeter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
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
            smoothedVolume = smoothedVolume * 0.8f + rawVolume * 0.2f
            val currentVolume = smoothedVolume.toInt()

            // 2. ЖЕСТКАЯ ПРОВЕРКА ПОРОГА (ГЛУШИМ ВСЁ, ЧТО ТИШЕ ПОРОГА)
            val threshold = prefsManager.minVolumeThreshold
            if (currentVolume < threshold) {
                smoothedRpm = 0f
                // Сбрасываем обороты в 0 и сразу отдаем в UI
                onUpdate(0, 0f, currentVolume, "Ожидание (тихо)...")
                continue
            }

            // 3. Анализ частоты и расчет оборотов
            var zeroCrossings = 0
            for (i in 1 until readCount) {
                if ((shortBuffer[i - 1] < 0 && shortBuffer[i] >= 0) || 
                    (shortBuffer[i - 1] >= 0 && shortBuffer[i] < 0)) {
                    zeroCrossings++
                }
            }

            val frequency = (zeroCrossings.toFloat() * sampleRate) / (2.0f * readCount)
            
            val engineType = prefsManager.engineType
            val rawRpm = when (engineType) {
                2 -> (frequency * 60f).toInt()
                4 -> (frequency * 30f).toInt()
                else -> (frequency * 60f).toInt()
            }

            val maxLimit = prefsManager.maxAllowedRpm
            val clampedRpm = rawRpm.coerceIn(0, maxLimit)

            val rise = prefsManager.riseTimeConstant
            val fall = prefsManager.fallTimeConstant
            val alpha = if (clampedRpm > smoothedRpm) rise else fall
            smoothedRpm = smoothedRpm + alpha * (clampedRpm - smoothedRpm)

            val finalRpm = smoothedRpm.toInt()
            val statusMsg = when (engineType) {
                2 -> "Работает (2T)"
                4 -> "Работает (4T)"
                else -> "Работает (Others)"
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
