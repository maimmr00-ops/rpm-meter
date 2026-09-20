package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rpm_prefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("engine_type", 2)
        set(value) = prefs.edit().putInt("engine_type", value).apply()

    var maxAllowedRpm: Int
        get() = prefs.getInt("max_rpm", 12000)
        set(value) = prefs.edit().putInt("max_rpm", value).apply()

    var audioBufferSize: Int
        get() = prefs.getInt("buffer_size", 2560)
        set(value) = prefs.edit().putInt("buffer_size", value).apply()

    fun hasStoredThreshold(): Boolean {
        return prefs.contains("min_volume")
    }

    var minVolumeThreshold: Int
        get() = prefs.getInt("min_volume", 20)
        set(value) = prefs.edit().putInt("min_volume", value).apply()

    var smoothPreset: Int
        get() = prefs.getInt("smooth_preset", 1)
        set(value) = prefs.edit().putInt("smooth_preset", value).apply()

    fun saveSmooth(rise: Float, fall: Float) {
        val preset = when {
            rise >= 0.5f -> 0
            rise >= 0.1f -> 1
            else -> 2
        }
        smoothPreset = preset
    }

    val riseTimeConstant: Float
        get() = when (smoothPreset) {
            0 -> 0.7f
            1 -> 0.3f
            else -> 0.05f
        }

    val fallTimeConstant: Float
        get() = riseTimeConstant
}
