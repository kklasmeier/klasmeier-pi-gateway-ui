package com.klasmeier.internetgatewaypath

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.monitor.PathMonitor
import com.klasmeier.internetgatewaypath.ui.main.MainScreen
import com.klasmeier.internetgatewaypath.ui.main.MainViewModel
import com.klasmeier.internetgatewaypath.ui.settings.SettingsScreen
import com.klasmeier.internetgatewaypath.ui.settings.SettingsViewModel
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
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(configured) {
                    if (configured) {
                        settingsRepository.migrateLegacyQuietHoursIfNeeded()
                        mainViewModel.refresh()
                        PathMonitor.start(applicationContext)
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                if (!configured) {
                    SetupScreen(onComplete = { })
                } else {
                    val navController = rememberNavController()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner, configured) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                mainViewModel.refreshOnForeground()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel = mainViewModel,
                                onOpenSettings = { navController.navigate("settings") },
                            )
                        }
                        composable("settings") {
                            val settingsViewModel: SettingsViewModel = viewModel()
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                onClearedSetup = {
                                    navController.popBackStack("main", inclusive = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
