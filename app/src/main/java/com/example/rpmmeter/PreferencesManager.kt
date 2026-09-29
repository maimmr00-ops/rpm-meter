package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    // 01: Инициализация хранилища настроек приложения (SharedPreferences)
    private val prefs: SharedPreferences = context.getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)

    // 10: Параметр выбора типа двигателя (2T, 4T, Others)
    var engineType: Int
        get() = prefs.getInt("key_engine_type", 2) // По умолчанию 2T (2)
        set(value) {
            prefs.edit().putInt("key_engine_type", value).apply()
            // При смене мотора проверяем, валиден ли текущий алгоритм, если нет — ставим дефолт для этого мотора
            if (!isAlgorithmAllowed(algorithmIndex, value)) {
                algorithmIndex = getDefaultAlgorithmForEngine(value)
            }
        }

    // Источник звука (0 = MIC, 1 = VOICE_RECOGNITION, 2 = UNPROCESSED)
    var audioSource: Int
        get() = prefs.getInt("key_audio_source", 0)
        set(value) = prefs.edit().putInt("key_audio_source", value).apply()

    // 78: Выбранный пользователем алгоритм анализа звука
    var algorithmIndex: Int
        get() {
            val saved = prefs.getInt("key_algorithm_index", 2)
            // Жесткая защита: если сохраненный индекс не подходит под текущий мотор, возвращаем дефолт
            if (!isAlgorithmAllowed(saved, engineType)) {
                return getDefaultAlgorithmForEngine(engineType)
            }
            return saved
        }
        set(value) = prefs.edit().putInt("key_algorithm_index", value).apply()

    // Логика подбора алгоритма по умолчанию под конкретный тип мотора
    fun getDefaultAlgorithmForEngine(engine: Int): Int {
        return when (engine) {
            2 -> 2 // 2T -> Spectral (индекс 2)
            4 -> 1 // 4T -> AutoCorr (индекс 1)
            else -> 0 // Others -> Zero-X (индекс 0)
        }
    }

    // Проверка совместимости алгоритма с типом двигателя
    fun isAlgorithmAllowed(algIndex: Int, engine: Int): Boolean {
        // Разблокируем все алгоритмы для теста:
        return true
    }

    fun hasStoredThreshold(): Boolean {
        return prefs.contains("key_min_threshold")
    }

    // 56: Порог чувствительности (минимальная громкость для срабатывания тахометра)
    var minVolumeThreshold: Int
        get() = prefs.getInt("key_min_threshold", 100)
        set(value) = prefs.edit().putInt("key_min_threshold", value).apply()

    // 34: Максимально допустимые обороты (лимит шкалы)
    var maxAllowedRpm: Int
        get() = prefs.getInt("key_max_rpm", 12000)
        set(value) = prefs.edit().putInt("key_max_rpm", value).apply()

    // 45: Размер аудио-буфера / скорость обновления данных
    var audioBufferSize: Int
        get() = prefs.getInt("key_buffer_size", 2560)
        set(value) = prefs.edit().putInt("key_buffer_size", value).apply()

    // 23: Пресет плавности тахометра (Off / Sharp / Norm / Soft) -> индексы 3, 0, 1, 2
    var smoothPreset: Int
        get() = prefs.getInt("key_smooth_preset", 1)
        set(value) = prefs.edit().putInt("key_smooth_preset", value).apply()

    fun saveSmooth(rise: Float, fall: Float) {
        val preset = when {
            rise >= 1.0f -> 3 // Off (мгновенный вывод)
            rise >= 0.5f -> 0 // Sharp
            rise >= 0.1f -> 1 // Norm
            else -> 2         // Soft
        }
        smoothPreset = preset
    }

    val riseTimeConstant: Float
        get() = when (smoothPreset) {
            3 -> 1.0f // Off: константа 1.0 означает отсутствие фильтрации (берем новое значение на 100%)
            0 -> 0.7f
            1 -> 0.3f
            else -> 0.05f
        }

    val fallTimeConstant: Float
        get() = riseTimeConstant
}
