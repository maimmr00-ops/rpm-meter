package com.example.rpmmeter

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(50, 50, 50, 50)
        }

        val titleText = TextView(this).apply {
            text = "RPM Meter: Готов к работе"
            textSize = 24f
            setTextColor(Color.WHITE)
        }

        layout.addView(titleText)
        setContentView(layout)
    }
}
