
package com.example.rpmmeter

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "RPM Meter работает!"
            textSize = 28f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DARK_GRAY)
            setPadding(50, 50, 50, 50)
        }
        
        setContentView(textView)
    }
}
