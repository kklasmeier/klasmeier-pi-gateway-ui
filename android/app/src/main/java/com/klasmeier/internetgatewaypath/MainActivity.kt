package com.klasmeier.internetgatewaypath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.ui.main.MainScreen
import com.klasmeier.internetgatewaypath.ui.main.MainViewModel
import com.klasmeier.internetgatewaypath.ui.setup.SetupScreen
import com.klasmeier.internetgatewaypath.ui.theme.InternetGatewayPathTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InternetGatewayPathTheme {
                val configured by settingsRepository.isConfigured.collectAsState(initial = false)
                if (configured) {
                    MainScreen(viewModel = mainViewModel)
                } else {
                    SetupScreen(onComplete = { recreate() })
                }
            }
        }
    }
}
