package com.example.rpmmeter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.rpmmeter.detectors.AmdfDetector
import com.example.rpmmeter.detectors.AutocorrelationDetector
import com.example.rpmmeter.detectors.CombFilterDetector
import com.example.rpmmeter.detectors.HpsDetector
import com.example.rpmmeter.detectors.HybridDetector
import com.example.rpmmeter.detectors.PitchDetector
import com.example.rpmmeter.detectors.SpectralDetector
import com.example.rpmmeter.detectors.YinDetector
import com.example.rpmmeter.detectors.ZeroCrossingDetector
import kotlin.math.sqrt

class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val onUpdate: (rawRpm: Float, allFreq: Float, preFreq: Float, volume: Int, status: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var analysisThread: Thread? = null
    private var smoothedVolume = 0f

    private val zeroCrossing = ZeroCrossingDetector()
    private val autoCorr = AutocorrelationDetector()
    private val spectral = SpectralDetector()
    private val hybrid = HybridDetector()
    private val yin = YinDetector()
    private val hps = HpsDetector()
    private val amdf = AmdfDetector()
    private val comb = CombFilterDetector()

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
                val readCount = try {
                    record.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    -1
                }

                if (readCount <= 0) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    continue
                }

                var sum = 0.0
                for (i in 0 until readCount) {
                    val v = buffer[i].toDouble()
                    sum += v * v
                }
                val rawVolume = sqrt(sum / readCount)
                smoothedVolume = smoothedVolume + 0.3f * (rawVolume.toFloat() - smoothedVolume)
                val currentVolInt = smoothedVolume.toInt()

                if (currentVolInt < prefsManager.minVolumeThreshold) {
                    onUpdate(0f, 0f, 0f, currentVolInt, "Тишина / Ниже порога")
                    continue
                }

                val allFreq = zeroCrossing.detect(buffer, readCount, sampleRate)

                val activeDetector: PitchDetector = when (prefsManager.algorithmIndex) {
                    0 -> zeroCrossing
                    1 -> autoCorr
                    2 -> spectral
                    3 -> hybrid
                    4 -> yin
                    5 -> hps
                    6 -> amdf
                    7 -> comb
                    else -> autoCorr
                }

                val preFreq = activeDetector.detect(buffer, readCount, sampleRate)

                if (preFreq < 10.0f || preFreq > 400.0f) {
                    onUpdate(0f, allFreq, 0f, currentVolInt, "Поиск сигнала...")
                    continue
                }

                val engineType = prefsManager.engineType
                val rawRpm = when (engineType) {
                    2 -> preFreq * 60.0f
                    4 -> preFreq * 30.0f
                    else -> preFreq * 60.0f
                }

                val maxAllowed = prefsManager.maxAllowedRpm.toFloat()
                if (rawRpm > maxAllowed) continue

                onUpdate(rawRpm, allFreq, preFreq, currentVolInt, "Работа мотора")
            }
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
}
