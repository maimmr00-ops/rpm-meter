package com.example.rpmmeter

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RpmPrefs", Context.MODE_PRIVATE)

    var engineType: Int
        get() = prefs.getInt("engineType", 2)
        set(value) = prefs.edit().putInt("engineType", value).apply()

    var maxAllowedRpm: Int
        get() = prefs.getInt("maxAllowedRpm", 12000)
        set(value) = prefs.edit().putInt("maxAllowedRpm", value).apply()

    var audioBufferSize: Int
        get() = prefs.getInt("audioBufferSize", 2560)
        set(value) = prefs.edit().putInt("audioBufferSize", value).apply()

    var riseTimeConstant: Float
        get() = prefs.getFloat("riseTime", 0.06f)
        set(value) = prefs.edit().putFloat("riseTime", value).apply()

    var fallTimeConstant: Float
        get() = prefs.getFloat("fallTime", 0.18f)
        set(value) = prefs.edit().putFloat("fallTime", value).apply()

    // Порог чувствительности по громкости (отсечение мелких фоновых звуков, от 20 до 200)
    var minVolumeThreshold: Int
        get() = prefs.getInt("minVolumeThreshold", 60)
        set(value) = prefs.edit().putInt("minVolumeThreshold", value).apply()

    fun saveSmooth(rise: Float, fall: Float) {
        val editor = prefs.edit()
        editor.putFloat("riseTime", rise)
        editor.putFloat("fallTime", fall)
        editor.apply()
    }
}
