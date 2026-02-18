package com.lanshare.android

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = getString(R.string.welcome_message)
            textSize = 20f
            setPadding(48, 96, 48, 96)
        }
        setContentView(view)
    }
}
