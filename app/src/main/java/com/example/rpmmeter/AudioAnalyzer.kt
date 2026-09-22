package com.example.rpmmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val selectedAlgorithmIndex: Int,
    private val onUpdate: (Int, Float, Int, String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var analysisThread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        analysisThread = Thread({
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                if (minBufferSize <= 0) {
                    onError("Ошибка буфера микрофона: $minBufferSize")
                    isRunning = false
                    return@Thread
                }
                
                val bufferSize = maxOf(minBufferSize, prefsManager.audioBufferSize)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Микрофон не инициализирован")
                    isRunning = false
                    return@Thread
                }

                audioRecord?.startRecording()
                val audioBuffer = ShortArray(bufferSize)

                while (isRunning) {
                    val currentRecord = audioRecord
                    if (currentRecord == null || currentRecord.state != AudioRecord.STATE_INITIALIZED) {
                        break
                    }

                    val readSize = currentRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (readSize > 0) {
                        
                        // Расчет RMS (громкость)
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val sample = audioBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readSize)
                        val volume = rms.toInt()

                        val minThreshold = prefsManager.minVolumeThreshold
                        var frequency = 0f
                        var rpm = 0
                        var statusStr = "Ожидание"

                        if (volume >= minThreshold) {
                            val engineType = prefsManager.engineType

                            // Выбор математики анализа
                            frequency = when (selectedAlgorithmIndex) {
                                // Алгоритм 0: Zero-Crossing
                                0 -> {
                                    var zeroCrossings = 0
                                    val noiseFloor = (volume * 0.15).toInt().toShort()
                                    var lastState = 0
                                    for (i in 0 until readSize) {
                                        val sample = audioBuffer[i]
                                        val currentState = when {
                                            sample > noiseFloor -> 1
                                            sample < -noiseFloor -> -1
                                            else -> lastState
                                        }
                                        if (lastState != 0 && currentState != 0 && currentState != lastState) {
                                            zeroCrossings++
                                        }
                                        if (currentState != 0) lastState = currentState
                                    }
                                    (zeroCrossings.toFloat() * sampleRate / (readSize * 2f))
                                }

                                // Алгоритм 1: Autocorrel
                                1 -> {
                                    val minLag = sampleRate / 400
                                    val maxLag = sampleRate / 15
                                    var bestLag = minLag
                                    var maxCorr = -1.0
                                    val limitSize = minOf(readSize, 1024)
                                    for (lag in minLag..minOf(maxLag, limitSize / 2)) {
                                        var corr = 0.0
                                        val n = limitSize - lag
                                        for (i in 0 until n) {
                                            corr += audioBuffer[i].toDouble() * audioBuffer[i + lag].toDouble()
                                        }
                                        if (corr > maxCorr) {
                                            maxCorr = corr
                                            bestLag = lag
                                        }
                                    }
                                    if (bestLag > 0) sampleRate.toFloat() / bestLag.toFloat() else 50.0f
                                }

                                // Алгоритм 2: AMDF
                                2 -> {
                                    val minLag = sampleRate / 400
                                    val maxLag = sampleRate / 15
                                    var bestLag = minLag
                                    var minVal = Double.MAX_VALUE
                                    val limitSize = minOf(readSize, 1024)
                                    for (lag in minLag..minOf(maxLag, limitSize / 2)) {
                                        var sumDiff = 0.0
                                        val n = limitSize - lag
                                        for (i in 0 until n) {
                                            sumDiff += abs(audioBuffer[i] - audioBuffer[i + lag])
                                        }
                                        val avgDiff = sumDiff / n
                                        if (avgDiff < minVal) {
                                            minVal = avgDiff
                                            bestLag = lag
                                        }
                                    }
                                    if (bestLag > 0) sampleRate.toFloat() / bestLag.toFloat() else 50.0f
                                }

                                // Алгоритм 3: Peak-Time
                                3 -> {
                                    var lastPeakIdx = -1
                                    var totalIntervals = 0
                                    var sumIntervals = 0f
                                    val peakThreshold = (volume * 0.6).toInt().toShort()
                                    for (i in 2 until readSize - 2) {
                                        if (audioBuffer[i] > peakThreshold && 
                                            audioBuffer[i] >= audioBuffer[i-1] && 
                                            audioBuffer[i] >= audioBuffer[i+1]) {
                                            if (lastPeakIdx != -1) {
                                                val interval = (i - lastPeakIdx).toFloat()
                                                if (interval > (sampleRate / 450f) && interval < (sampleRate / 10f)) {
                                                    sumIntervals += interval
                                                    totalIntervals++
                                                }
                                            }
                                            lastPeakIdx = i
                                        }
                                    }
                                    if (totalIntervals > 0) {
                                        sampleRate.toFloat() / (sumIntervals / totalIntervals)
                                    } else {
                                        50.0f
                                    }
                                }

                                else -> 50.0f
                            }

                            frequency = frequency.coerceIn(5.0f, 500.0f)

                            rpm = when (engineType) {
                                4 -> (frequency * 30).toInt()
                                2 -> (frequency * 60).toInt()
                                else -> (frequency * 60).toInt()
                            }
                            
                            val modeName = when (engineType) {
                                2 -> "2T"
                                4 -> "4T"
                                else -> "Others"
                            }
                            statusStr = "$modeName | Алг ${selectedAlgorithmIndex + 1}"
                        } else {
                            statusStr = "Ожидание / Тишина"
                        }

                        onUpdate(rpm, frequency, volume, statusStr)
                    }
                    Thread.sleep(10)
                }
            } catch (e: SecurityException) {
                onError("Нет разрешения на микрофон")
            } catch (e: Exception) {
                onError("Ошибка звука: ${e.localizedMessage}")
            } finally {
                stopInternal()
            }
        }, "AudioAnalyzerWorkerThread")

        analysisThread?.start()
    }

    fun stop() {
        isRunning = false
        analysisThread?.interrupt()
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }
}
