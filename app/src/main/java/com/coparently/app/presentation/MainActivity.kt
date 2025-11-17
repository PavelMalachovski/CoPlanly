package com.coparently.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.coparently.app.data.notification.NotificationManager
import com.coparently.app.presentation.navigation.NavGraph
import com.coparently.app.presentation.theme.CoParentlyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for CoParently app.
 * Entry point of the application.
 * Handles Google Sign-In result, Push Notifications, and Splash Screen (Android 12+).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationManager: NotificationManager

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, initialize notifications
            notificationManager.registerToken()
        }
    }

    // Use Activity Result API instead of deprecated onActivityResult
    private val signInResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Result handling moved to SettingsScreen via Activity Result API
        // This launcher is kept for backward compatibility if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super.onCreate()
        // This ensures the splash screen is displayed on Android 12+
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for modern Android UI
        // This makes the app draw behind the system bars
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded()

        // Initialize notifications
        notificationManager.initializeNotifications()

        setContent {
            CoParentlyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    /**
     * Requests notification permission on Android 13+ (API 33+).
     * For older versions, permission is granted by default.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

