package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RpmMeterPrefs", Context.MODE_PRIVATE)

    var algorithmIndex: Int
        get() = prefs.getInt("key_algorithm_index", 0)
        set(value) = prefs.edit().putInt("key_algorithm_index", value).apply()

    // 2 = 2T, 4 = 4T, 0 или другой = Others
    var engineType: Int
        get() = prefs.getInt("key_engine_type", 2)
        set(value) = prefs.edit().putInt("key_engine_type", value).apply()

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
        get() = prefs.getInt("key_smooth_preset", 1) // 0: Sharp, 1: Norm, 2: Soft
        set(value) = prefs.edit().putInt("key_smooth_preset", value).apply()

    fun hasStoredThreshold(): Boolean {
        return prefs.contains("key_min_threshold")
    }
}
