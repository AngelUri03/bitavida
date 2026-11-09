package com.bitavida.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.theme.BitavidaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitavidaTheme {
                TodayDemoScreen()
            }
        }
    }
}
