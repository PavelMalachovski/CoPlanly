package com.coparently.app.di

import android.content.Context
import com.coparently.app.data.security.EncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing security-related dependencies.
 *
 * `SecurityAudit` used to be provided here too. It was injected nowhere, half its checks were
 * `TODO` stubs, and `isCertificatePinningEnabled()` returned a hard-coded `true` — a
 * self-assessment that would have reported the app as pinned. Deleted rather than kept as a
 * reminder (September 2026).
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEncryptionManager(
        @ApplicationContext context: Context
    ): EncryptionManager {
        return EncryptionManager(context)
    }
}
