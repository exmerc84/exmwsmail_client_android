package com.example.exmwsmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.exmwsmail.ui.RootContent
import com.example.exmwsmail.ui.theme.EXMWSMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EXMWSMailTheme {
                RootContent()
            }
        }
    }
}
