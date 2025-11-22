package com.coparently.app.di

import android.content.Context
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.utils.security.SecurityAudit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing security-related dependencies.
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

    @Provides
    @Singleton
    fun provideSecurityAudit(
        @ApplicationContext context: Context,
        crashlyticsManager: CrashlyticsManager,
        encryptionManager: EncryptionManager
    ): SecurityAudit {
        return SecurityAudit(context, crashlyticsManager, encryptionManager)
    }
}
