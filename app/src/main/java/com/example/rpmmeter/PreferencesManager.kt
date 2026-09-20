package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rpm_prefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("engine_type", 2) // По умолчанию 2T
        set(value) = prefs.edit().putInt("engine_type", value).apply()

    var maxAllowedRpm: Int
        get() = prefs.getInt("max_rpm", 12000) // По умолчанию 12k
        set(value) = prefs.edit().putInt("max_rpm", value).apply()

    var audioBufferSize: Int
        get() = prefs.getInt("buffer_size", 2560) // По умолчанию Norm
        set(value) = prefs.edit().putInt("buffer_size", value).apply()

    fun hasStoredThreshold(): Boolean {
        return prefs.contains("min_volume")
    }

    // Порог громкости (по умолчанию 20 — первый квадрат)
    var minVolumeThreshold: Int
        get() = prefs.getInt("min_volume", 20)
        set(value) = prefs.edit().putInt("min_volume", value).apply()

    val riseTimeConstant: Float
        get() = prefs.getFloat("rise_alpha", 0.06f)

    val fallTimeConstant: Float
        get() = prefs.getFloat("fall_alpha", 0.18f)

    fun saveSmooth(rise: Float, fall: Float) {
        prefs.edit()
            .putFloat("rise_alpha", rise)
            .putFloat("fall_alpha", fall)
            .apply()
    }
}
