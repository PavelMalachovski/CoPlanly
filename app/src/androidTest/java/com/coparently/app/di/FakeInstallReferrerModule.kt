package com.coparently.app.di

import com.coparently.app.domain.pairing.InstallReferrerSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [InstallReferrerModule] in instrumented tests: no test talks to Google Play's referrer
 * service, and none starts with a pairing code it did not ask for.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [InstallReferrerModule::class])
object FakeInstallReferrerModule {

    /** A referrer source that never has a code. */
    @Provides
    @Singleton
    fun provideInstallReferrerSource(): InstallReferrerSource = object : InstallReferrerSource {
        override suspend fun pendingPairingCode(): String? = null

        override fun markHandedOff() = Unit
    }
}
