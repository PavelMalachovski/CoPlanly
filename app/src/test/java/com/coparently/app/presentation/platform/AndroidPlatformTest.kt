package com.coparently.app.presentation.platform

import androidx.compose.ui.test.junit4.createComposeRule
import com.coparently.app.BuildConfig
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The provider `MainActivity`, `QRScannerActivity` and the screenshot frame wrap their content in
 * (MON-24 phase 2, Task 11) covers every platform local: a local it missed would throw the first
 * time a screen read it, on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class AndroidPlatformTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the Android provider provides every platform local`() {
        val read = mutableListOf<Any?>()
        rule.setContent {
            ProvideAndroidPlatform {
                PlatformLocals.all.forEach { read += it.current }
            }
        }
        rule.waitForIdle()

        assertEquals(PlatformLocals.all.size, read.size)
        assertTrue(read.none { it == null })
    }

    @Test
    fun `the build values are BuildConfig's, a blank address read as none`() {
        val values = androidBuildValues()

        assertEquals(BuildConfig.VERSION_NAME, values.versionName)
        assertEquals(BuildConfig.PAIRING_WEB_LINK, values.pairingWebLink)
        assertEquals(BuildConfig.EDUPAGE_IMPORT, values.edupageImport)
        assertEquals(BuildConfig.PRIVACY_POLICY_URL.ifBlank { null }, values.privacyPolicyUrl)
        assertEquals(BuildConfig.TERMS_URL.ifBlank { null }, values.termsUrl)
        if (BuildConfig.SUPPORT_EMAIL.isBlank()) assertNull(values.supportEmail)
    }
}
