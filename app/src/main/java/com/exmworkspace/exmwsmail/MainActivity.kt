package com.exmworkspace.exmwsmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.exmworkspace.exmwsmail.ui.RootContent
import com.exmworkspace.exmwsmail.ui.theme.EXMWSMailTheme

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
