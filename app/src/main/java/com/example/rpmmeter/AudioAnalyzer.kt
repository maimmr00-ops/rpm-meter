package com.example.rpmmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rpm: Int, freq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var isRecording = false

    fun start() {
        isRecording = true
        thread {
            val sampleRate = 8000
            val channel = AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            var recorder: AudioRecord? = null

            try {
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, format)
                if (minBuf <= 0) {
                    onError("Ошибка: Микрофон не поддерживается")
                    return@thread
                }

                val volumeThreshold = 30
                var smoothedRpm = 0f

                while (isRecording) {
                    val currentBufSize = maxOf(minBuf, prefsManager.audioBufferSize)
                    try {
                        recorder?.stop()
                        recorder?.release()
                    } catch (_: Exception) {}

                    recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, format, currentBufSize)
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        onError("Ошибка инициализации микрофона")
                        Thread.sleep(1000)
                        continue
                    }

                    recorder.startRecording()
                    val buffer = ShortArray(currentBufSize)
                    val dt = currentBufSize.toFloat() / sampleRate.toFloat()

                    while (isRecording && prefsManager.audioBufferSize == currentBufSize) {
                        val readSize = recorder.read(buffer, 0, currentBufSize)
                        if (readSize <= 0) continue

                        var volume: Long = 0
                        for (i in 0 until readSize) volume += abs(buffer[i].toLong())
                        val avgVol = (volume / readSize).toInt()

                        var rawRpm = 0
                        var dominantFreq = 0f

                        if (avgVol > volumeThreshold) {
                            val minLag = sampleRate / 200
                            val maxLag = sampleRate / 15
                            var bestLag = -1
                            var maxCorr: Long = 0

                            var lag = minLag
                            while (lag <= maxLag) {
                                var corr: Long = 0
                                val limit = readSize - lag
                                var i = 0
                                while (i < limit) {
                                    corr += buffer[i].toLong() * buffer[i + lag].toLong()
                                    i++
                                }
                                if (corr > maxCorr) {
                                    maxCorr = corr
                                    bestLag = lag
                                }
                                lag++
                            }

                            if (bestLag > 0) {
                                dominantFreq = sampleRate.toFloat() / bestLag
                                val calcRpm = when (prefsManager.engineType) {
                                    4 -> (dominantFreq * 120).toInt()
                                    else -> (dominantFreq * 60).toInt()
                                }
                                if (calcRpm in 500..prefsManager.maxAllowedRpm) rawRpm = calcRpm
                            }
                        }

                        if (rawRpm > 0) {
                            if (smoothedRpm == 0f) {
                                smoothedRpm = rawRpm.toFloat()
                            } else {
                                val alpha = (1.0 - exp((-dt / prefsManager.riseTimeConstant).toDouble())).toFloat()
                                smoothedRpm += alpha * (rawRpm - smoothedRpm)
                            }
                        } else {
                            val dropAlpha = (1.0 - exp((-dt / prefsManager.dropTimeConstant).toDouble())).toFloat()
                            smoothedRpm *= (1f - dropAlpha)
                            if (smoothedRpm < 300) smoothedRpm = 0f
                        }

                        val finalRpm = smoothedRpm.toInt()
                        val status = if (finalRpm > 0) {
                            val modeName = if (prefsManager.engineType == 4) "4T" else if (prefsManager.engineType == 3) "Электро" else "2T"
                            "Работает ($modeName)"
                        } else {
                            if (avgVol > volumeThreshold) "Анализ тона..." else "Ожидание запуска мотора..."
                        }

                        onUpdate(finalRpm, dominantFreq, avgVol, status)
                    }
                }
            } catch (e: Exception) {
                onError("Ошибка: ${e.message}")
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        isRecording = false
    }
}
