package com.example.rpmmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rpm: Int, freq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var thread: Thread? = null

    private var smoothedRpm = 0f

    fun start() {
        if (isRunning) return
        isRunning = true

        thread = Thread {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            val bufferSize = prefsManager.audioBufferSize * 2

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize.coerceAtLeast(AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat))
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Ошибка инициализации микрофона")
                    return@Thread
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize)

                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        // 1. Расчет громкости (RMS)
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / readSize)
                        val volume = rms.toInt()

                        // Проверяем порог громкости (шумовой фильтр)
                        val threshold = prefsManager.minVolumeThreshold
                        if (volume < threshold) {
                            smoothedRpm = 0f
                            onUpdate(0, 0f, volume, "Ожидание (тихо)...")
                            continue
                        }

                        // 2. Подсчет частоты методом пересечения нуля (Zero-Crossing)
                        var zeroCrossings = 0
                        for (i in 1 until readSize) {
                            if ((buffer[i - 1] < 0 && buffer[i] >= 0) || (buffer[i - 1] >= 0 && buffer[i] < 0)) {
                                zeroCrossings++
                            }
                        }

                        val durationSeconds = readSize.toFloat() / sampleRate
                        val frequency = (zeroCrossings.toFloat() / 2.0f) / durationSeconds

                        val engineType = prefsManager.engineType
                        val rawRpm = when (engineType) {
                            2 -> (frequency * 60).toInt()
                            4 -> (frequency * 120).toInt()
                            else -> (frequency * 60).toInt()
                        }

                        val clampedRpm = rawRpm.coerceIn(0, prefsManager.maxAllowedRpm)

                        // Динамическая фильтрация с использованием riseTimeConstant и fallTimeConstant
                        val rise = prefsManager.riseTimeConstant
                        val fall = prefsManager.fallTimeConstant

                        smoothedRpm = if (clampedRpm > smoothedRpm) {
                            smoothedRpm + (clampedRpm - smoothedRpm) * rise
                        } else {
                            smoothedRpm + (clampedRpm - smoothedRpm) * fall
                        }

                        val finalRpm = smoothedRpm.toInt()
                        val statusMsg = when (engineType) {
                            2 -> "Работает (2T)"
                            4 -> "Работает (4T)"
                            else -> "Работает (Others)"
                        }

                        onUpdate(finalRpm, frequency, volume, statusMsg)
                    }
                }
            } catch (e: SecurityException) {
                onError("Нет доступа к микрофону")
            } catch (e: Exception) {
                onError("Ошибка: ${e.localizedMessage}")
            }
        }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        thread?.join(500)
    }
}
