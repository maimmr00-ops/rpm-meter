package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("key_engine_type", 2) // По умолчанию 2T (2)
        set(value) {
            prefs.edit().putInt("key_engine_type", value).apply()
            // При смене мотора проверяем, валиден ли текущий алгоритм, если нет — ставим дефолт для этого мотора
            if (!isAlgorithmAllowed(algorithmIndex, value)) {
                algorithmIndex = getDefaultAlgorithmForEngine(value)
            }
        }

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

    fun getDefaultAlgorithmForEngine(engine: Int): Int {
        return when (engine) {
            2 -> 2 // 2T -> AMDF (индекс 2)
            4 -> 1 // 4T -> Autocorrel (индекс 1)
            else -> 0 // Others -> Zero-Cross (индекс 0)
        }
    }

    fun isAlgorithmAllowed(algIndex: Int, engine: Int): Boolean {
        return when (engine) {
            2 -> algIndex == 0 || algIndex == 2 // 2T: Zero-Cross & AMDF
            4 -> algIndex == 1 || algIndex == 3 // 4T: Autocorrel & Peak-Time
            else -> algIndex == 0 || algIndex == 1 // Others: Zero-Cross & Autocorrel
        }
    }

    var minVolumeThreshold: Int
        get() = prefs.getInt("key_min_threshold", 100)
        set(value) = prefs.edit().putInt("key_min_threshold", value).apply()

    var maxAllowedRpm: Int
        get() = prefs.getInt("key_max_rpm", 12000)
        set(value) = prefs.edit().putInt("key_max_rpm", value).apply()

    var audioBufferSize: Int
        get() = prefs.getInt("key_buffer_size", 2560)
        set(value) = prefs.edit().putInt("key_buffer_size", value).apply()

    var smoothPreset: Int
        get() = prefs.getInt("key_smooth_preset", 1)
        set(value) = prefs.edit().putInt("key_smooth_preset", value).apply()
}
