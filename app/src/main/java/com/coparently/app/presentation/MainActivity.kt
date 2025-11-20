package com.coparently.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.coparently.app.data.notification.NotificationManager
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.presentation.navigation.NavGraph
import com.coparently.app.presentation.theme.CoParentlyTheme
import com.coparently.app.presentation.sync.SyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompositionLocal for providing Google Sign-In callback throughout the app.
 */
val LocalGoogleSignInCallback = staticCompositionLocalOf<((android.content.Intent) -> Unit)?> {
    null
}


/**
 * Main Activity for CoParently app.
 * Entry point of the application.
 * Handles Google Sign-In result, Push Notifications, and Splash Screen (Android 12+).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private val _darkThemeState = MutableStateFlow<Boolean?>(null)
    private val darkThemeState: StateFlow<Boolean?> = _darkThemeState

    private val syncViewModel: SyncViewModel by viewModels()

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, initialize notifications
            notificationManager.registerToken()
        }
    }

    // Google Sign-In Activity Result launcher for sync
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            lifecycleScope.launch {
                syncViewModel.handleSignInResult(task)
            }
        } else {
            val isCanceled = result.resultCode == RESULT_CANCELED
            val message = if (isCanceled) {
                getString(com.coparently.app.R.string.sync_google_sign_in_cancelled)
            } else {
                getString(com.coparently.app.R.string.sync_google_sign_in_failed)
            }
            Log.w("MainActivity", "Google sign-in aborted: resultCode=${result.resultCode}")
            syncViewModel.handleSignInCancellation(message)
        }
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

        // Load theme preference
        lifecycleScope.launch {
            preferencesRepository.getDarkThemeFlow().collect { isDark ->
                _darkThemeState.value = isDark
            }
        }

        setContent {
            val darkTheme by darkThemeState.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()

            // Use saved preference or fall back to system default
            val useDarkTheme = darkTheme ?: systemDarkTheme

            // Provide Google Sign-In callback through CompositionLocal
            val googleSignInCallback: (android.content.Intent) -> Unit = remember(googleSignInLauncher) {
                { intent ->
                    googleSignInLauncher.launch(intent)
                }
            }

            CoParentlyTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    CompositionLocalProvider(
                        LocalGoogleSignInCallback provides googleSignInCallback
                    ) {
                        NavGraph(
                            navController = navController,
                            syncViewModel = syncViewModel
                        )
                    }
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

