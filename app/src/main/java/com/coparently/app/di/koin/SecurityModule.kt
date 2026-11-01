package com.coparently.app.di.koin

import com.coparently.app.data.security.EncryptionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Security-related dependencies: the Keystore key the database passphrase and the preference
 * store are sealed with.
 *
 * `SecurityAudit` used to be provided here too. It was injected nowhere, half its checks were
 * `TODO` stubs, and `isCertificatePinningEnabled()` returned a hard-coded `true` — a
 * self-assessment that would have reported the app as pinned. Deleted rather than kept as a
 * reminder (September 2026).
 */
val securityModule = module {
    single { EncryptionManager(androidContext()) }
}
