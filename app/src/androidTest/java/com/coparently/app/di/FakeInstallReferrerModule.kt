package com.coparently.app.di

import com.coparently.app.domain.pairing.InstallReferrerSource
import org.koin.dsl.module

/**
 * Overrides `installReferrerModule` in instrumented tests: no test talks to Google Play's
 * referrer service, and none starts with a pairing code it did not ask for.
 */
val fakeInstallReferrerModule = module {
    // A referrer source that never has a code.
    single<InstallReferrerSource> {
        object : InstallReferrerSource {
            override suspend fun pendingPairingCode(): String? = null

            override fun markHandedOff() = Unit
        }
    }
}
