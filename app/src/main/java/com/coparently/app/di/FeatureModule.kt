package com.coparently.app.di

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.domain.feature.FeatureManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing feature flag and A/B testing dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object FeatureModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        return FirebaseRemoteConfig.getInstance()
    }

    @Provides
    @Singleton
    fun provideFeatureManager(
        preferences: EncryptedPreferences,
        remoteConfig: FirebaseRemoteConfig,
        analyticsManager: AnalyticsManager
    ): FeatureManager {
        return FeatureManager(preferences, remoteConfig, analyticsManager)
    }
}
