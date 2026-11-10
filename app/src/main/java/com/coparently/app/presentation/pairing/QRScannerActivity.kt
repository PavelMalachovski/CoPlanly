package com.coparently.app.presentation.pairing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.utils.AppLocale
import com.coparently.app.utils.inAppLanguage

/**
 * Full-screen QR scanner for pairing.
 *
 * Returns `RESULT_OK` with [EXTRA_CODE] set to a validated 6-character invite
 * code. Redeeming it is the caller's job — this activity only reads the camera.
 *
 * Stays an [AppCompatActivity] so per-app language selection keeps applying
 * (see the i18n rules in CLAUDE.md), and speaks the language [AppLocale] decides, as
 * `MainActivity` does.
 */
class QRScannerActivity : AppCompatActivity() {

    /** The base context in the app's language, before AppCompat's delegate wraps it. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inAppLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocale.follow(this)
        setContent {
            // The platform capabilities, as `MainActivity` provides them (MON-24 phase 2, Task 11).
            ProvideAndroidPlatform {
                CoPlanlyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        QrScannerScreen(
                            onCodeScanned = { code ->
                                setResult(RESULT_OK, Intent().putExtra(EXTRA_CODE, code))
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Result extra holding the scanned invite code. */
        const val EXTRA_CODE = "pairing_code"
    }
}
