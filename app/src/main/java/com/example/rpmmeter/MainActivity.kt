package com.example.rpmmeter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private var audioAnalyzer: AudioAnalyzer? = null
    
    private lateinit var tvRpmValue: TextView
    private lateinit var tvStatusValue: TextView
    
    private lateinit var headerBuilder: HeaderBuilder
    private lateinit var uiBuilder: UIBuilder
    
    private var isRunning = false

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- ПЕРЕХВАТЧИК ОШИБОК ДЛЯ СОХРАНЕНИЯ В ФАЙЛ ---
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                
                val logFile = java.io.File(getExternalFilesDir(null), "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            oldHandler?.uncaughtException(thread, throwable)
        }
        // ----------------------------------------------

        try {
            prefsManager = PreferencesManager(this)

            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#121212"))
                setPadding(12, 12, 12, 12)
            }
            setContentView(rootLayout)

            // Инициализация строителей интерфейса
            headerBuilder = HeaderBuilder(this, prefsManager) { selectedAlgIndex ->
                prefsManager.algorithmIndex = selectedAlgIndex
                restartAnalyzer()
            }

            tvRpmValue = TextView(this).apply {
                text = "0 RPM"
                textSize = 36f
                setTextColor(Color.parseColor("#00E676"))
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            tvStatusValue = TextView(this).apply {
                text = "Инициализация..."
                textSize = 12f
                setTextColor(Color.parseColor("#B0BEC5"))
                gravity = Gravity.CENTER
            }
            
            headerBuilder.infoPanel.addView(tvRpmValue)
            headerBuilder.infoPanel.addView(tvStatusValue)

            uiBuilder = UIBuilder(this, prefsManager) {
                headerBuilder.updateButtonStates()
                restartAnalyzer()
            }

            rootLayout.addView(headerBuilder.topPanel)
            rootLayout.addView(headerBuilder.infoPanel)
            rootLayout.addView(uiBuilder.table)
            rootLayout.addView(headerBuilder.algorithmRow)

            val copyright = TextView(this).apply {
                text = "2026 © YouTube_VRT \"Рациональный Труд\" | ver 2.2"
                textSize = 11f
                setTextColor(Color.parseColor("#9E9E9E"))
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 8)
            }
            rootLayout.addView(copyright)

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка UI: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAudioPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        stopAnalyzer()
    }

    private fun checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            startAnalyzer()
        }
    }

    private fun startAnalyzer() {
        if (isRunning) return
        isRunning = true

        try {
            audioAnalyzer = AudioAnalyzer(
                prefsManager = prefsManager,
                selectedAlgorithmIndex = prefsManager.algorithmIndex,
                onUpdate = { rpm, _, _, status ->
                    runOnUiThread {
                        tvRpmValue.text = "$rpm RPM"
                        tvStatusValue.text = status
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        tvStatusValue.text = "Ошибка: $err"
                    }
                }
            )
            audioAnalyzer?.start()
        } catch (e: Exception) {
            isRunning = false
            tvStatusValue.text = "Сбой потока: ${e.localizedMessage}"
        }
    }

    private fun stopAnalyzer() {
        if (!isRunning) return
        isRunning = false
        try {
            audioAnalyzer?.stop()
        } catch (_: Exception) {}
        audioAnalyzer = null
    }

    fun restartAnalyzer() {
        stopAnalyzer()
        startAnalyzer()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAnalyzer()
            } else {
                Toast.makeText(this, "Требуется доступ к микрофону!", Toast.LENGTH_LONG).show()
                tvStatusValue.text = "Нет доступа к микрофону"
            }
        }
    }
}
