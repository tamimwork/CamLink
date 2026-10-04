package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.SettingsRepository
import com.example.ui.navigation.CamLinkNavHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDeep

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = SettingsRepository(applicationContext)

        setContent {
            val settings by repository.streamSettings.collectAsState(
                initial = com.example.data.model.StreamSettings()
            )

            val isDarkTheme = when (settings.appTheme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDeep
                ) {
                    CamLinkNavHost()
                }
            }
        }
    }
}
