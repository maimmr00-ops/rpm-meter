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
        get() = prefs.getInt("audioBufferSize", 1536)
        set(value) = prefs.edit().putInt("audioBufferSize", value).apply()

    var riseTimeConstant: Float
        get() = prefs.getFloat("riseTimeConstant", 0.06f)
        set(value) = prefs.edit().putFloat("riseTimeConstant", value).apply()

    var dropTimeConstant: Float
        get() = prefs.getFloat("dropTimeConstant", 0.18f)
        set(value) = prefs.edit().putFloat("dropTimeConstant", value).apply()

    fun saveSmooth(rise: Float, drop: Float) {
        prefs.edit()
            .putFloat("riseTimeConstant", rise)
            .putFloat("dropTimeConstant", drop)
            .apply()
    }
}
