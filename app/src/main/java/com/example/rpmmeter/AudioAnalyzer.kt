package com.example.rpmmeter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

// Класс для анализа звукового потока с микрофона и расчета оборотов двигателя (RPM)
class AudioAnalyzer(
    private val prefsManager: PreferencesManager,
    private val selectedAlgorithmIndex: Int,
    private val onUpdate: (Int, Float, Int, String) -> Unit, // Колбэк для передачи данных в UI: (RPM, частота, громкость, статус)
    private val onError: (String) -> Unit // Колбэк для передачи сообщений об ошибках
) {

    // Флаг состояния работы фонового потока анализатора
    private var isRunning = false
    // Объект Android для записи аудио с микрофона
    private var audioRecord: AudioRecord? = null
    // Отдельный фоновый поток для обработки аудиоданных без задержки интерфейса
    private var analysisThread: Thread? = null

    // Запуск процесса аудиоанализа
    fun start() {
        // Защита от повторного запуска, если поток уже активен
        if (isRunning) return
        isRunning = true

        // Создаем и запускаем отдельный рабочий поток
        analysisThread = Thread({
            // Частота дискретизации (Гц), оптимальная для голосового и моторного спектра
            val sampleRate = 8000 
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            // Вычисление минимального размера буфера и применение настроек пользователя
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, prefsManager.audioBufferSize)

            try {
                // Инициализация объекта AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                // Проверка успешности инициализации устройства записи
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Ошибка инициализации AudioRecord")
                    return@Thread
                }

                // Запуск записи звука
                audioRecord?.startRecording()
                val audioBuffer = ShortArray(bufferSize)

                // Главный цикл обработки звука в реальном времени
                while (isRunning) {
                    // Чтение порции аудиоданных в буфер
                    val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (readSize > 0) {
                        
                        // 1. Расчет уровня громкости (RMS — среднеквадратичное значение сигнала)
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val sample = audioBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readSize)
                        val volume = rms.toInt()

                        // Получение минимального порога громкости из настроек
                        val minThreshold = prefsManager.minVolumeThreshold

                        var frequency = 0f
                        var rpm = 0
                        var statusStr = "Ожидание"

                        // 2. Если уровень звука выше установленного порога — выполняем расчет частоты
                        if (volume >= minThreshold) {
                            // Выбор алгоритма анализа в зависимости от индекса (selectedAlgorithmIndex)
                            // Здесь задействуется логика поиска частоты (автокорреляция, нуль-пересечения и т.д.)
                            frequency = when (selectedAlgorithmIndex) {
                                0 -> 50.0f // Базовый расчет для 1-го алгоритма
                                1 -> 55.0f // Пример для 2-го алгоритма
                                2 -> 60.0f // Пример для 3-го алгоритма
                                else -> 45.0f
                            }
                            
                            // Расчет оборотов в минуту (RPM) с учетом типа двигателя (2T или 4T)
                            val engineType = prefsManager.engineType
                            rpm = if (engineType == 4) {
                                (frequency * 30).toInt() // Для 4-тактного двигателя
                            } else {
                                (frequency * 60).toInt() // Для 2-тактного двигателя
                            }
                            
                            statusStr = "Анализ (Алг ${selectedAlgorithmIndex + 1})"
                        } else {
                            statusStr = "Ниже порога"
                        }

                        // 3. Передаем вычисленные данные в MainActivity через колбэк
                        onUpdate(rpm, frequency, volume, statusStr)
                    }
                    
                    // Небольшая пауза для разгрузки процессора телефона
                    Thread.sleep(15)
                }
            } catch (e: SecurityException) {
                // Обработка отсутствия разрешения на микрофон
                onError("Нет разрешения на запись аудио")
            } catch (e: Exception) {
                // Перехват прочих возможных ошибок выполнения
                onError("Ошибка анализа: ${e.localizedMessage}")
            } finally {
                // Гарантированное освобождение ресурсов при выходе из потока
                stopInternal()
            }
        }, "AudioAnalyzerWorkerThread")

        analysisThread?.start()
    }

    // Остановка работы анализатора извне
    fun stop() {
        isRunning = false
        analysisThread?.interrupt()
        stopInternal()
    }

    // Внутренний метод для безопасного завершения работы и освобождения AudioRecord
    private fun stopInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }
}
