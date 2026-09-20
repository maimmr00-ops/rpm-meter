package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rpm_prefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("engine_type", 2)
        set(value) = prefs.edit().putInt("engine_type", value).apply()

    var maxAllowedRpm: Int
        get() = prefs.getInt("max_allowed_rpm", 12000)
        set(value) = prefs.edit().putInt("max_allowed_rpm", value).apply()

    var audioBufferSize: Int
        get() = prefs.getInt("audio_buffer_size", 2560)
        set(value) = prefs.edit().putInt("audio_buffer_size", value).apply()

    var riseTimeConstant: Float
        get() = prefs.getFloat("rise_time", 0.06f)
        set(value) = prefs.edit().putFloat("rise_time", value).apply()

    var fallTimeConstant: Float
        get() = prefs.getFloat("fall_time", 0.18f)
        set(value) = prefs.edit().putFloat("fall_time", value).apply()

    var minVolumeThreshold: Int
        get() = prefs.getInt("min_volume_threshold", 8000) // По умолчанию жесткий порог
        set(value) = prefs.edit().putInt("min_volume_threshold", value).apply()

    fun saveSmooth(rise: Float, fall: Float) {
        prefs.edit().putFloat("rise_time", rise).putFloat("fall_time", fall).apply()
    }
}
