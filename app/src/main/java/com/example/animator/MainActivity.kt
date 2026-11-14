package com.example.animator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.animator.storage.ProjectStore
import com.example.animator.views.AnimatorRoot
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Black.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Black.toArgb())
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00ACC1),
                    background = Color.Black,
                    surface = Color.Black
                )
            ) {
                AnimatorRoot()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save anything not yet saved when the app goes to the background.
        val s = App.state
        if (s != null) {
            try {
                runBlocking { ProjectStore.saveIfNeeded(applicationContext, s) }
            } catch (_: Throwable) {
            }
        }
    }
}