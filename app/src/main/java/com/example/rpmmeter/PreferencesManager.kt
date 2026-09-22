package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("key_engine_type", 2) // По умолчанию 2T (2)
        set(value) {
            prefs.edit().putInt("key_engine_type", value).apply()
            // Автоматически назначаем лучший алгоритм по умолчанию при смене типа мотора
            algorithmIndex = getDefaultAlgorithmForEngine(value)
        }

    var algorithmIndex: Int
        get() = prefs.getInt("key_algorithm_index", 2) // Дефолт для 2T = AMDF (2)
        set(value) = prefs.edit().putInt("key_algorithm_index", value).apply()

    // Возвращает пару лучших алгоритмов и дефолт для каждого типа двигателя
    fun getDefaultAlgorithmForEngine(engine: Int): Int {
        return when (engine) {
            2 -> 2 // 2T -> AMDF (индекс 2)
            4 -> 1 // 4T -> Autocorrel (индекс 1)
            else -> 0 // Others -> Zero-Cross (индекс 0)
        }
    }

    // Проверка, разрешен ли алгоритм для текущего типа двигателя
    fun isAlgorithmAllowed(algIndex: Int, engine: Int): Boolean {
        return when (engine) {
            2 -> algIndex == 0 || algIndex == 2 // Для 2T: Zero-Cross (0) и AMDF (2)
            4 -> algIndex == 1 || algIndex == 3 // Для 4T: Autocorrel (1) и Peak-Time (3)
            else -> algIndex == 0 || algIndex == 1 // Для Others: Zero-Cross (0) и Autocorrel (1)
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
