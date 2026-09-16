package com.getevapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getevapp.ui.GetEverythingApp
import com.getevapp.ui.GetEverythingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0xFF6C28FE.toInt()), navigationBarStyle = SystemBarStyle.dark(0xFF6C28FE.toInt()))
        setContent { GetEverythingTheme { GetEverythingApp(viewModel()) } }
    }
}
