package com.coparently.app.di

import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing Firebase dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Provides Firebase Authentication instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    /**
     * Provides Firebase Firestore instance.
     * Offline persistence is enabled by default in newer versions of Firestore.
     * The app will automatically cache data locally for offline access.
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        // Offline persistence is enabled by default in Firestore SDK v24.0.0+
        // No need to explicitly enable it
        return FirebaseFirestore.getInstance()
    }

    /**
     * Provides Firebase Messaging instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }

    /**
     * Provides Firebase Analytics instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics {
        return Firebase.analytics
    }

    /**
     * Provides Firebase Crashlytics instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance()
    }

    /**
     * Provides QR Code service for generating pairing QR codes.
     */
    @Provides
    @Singleton
    fun provideQRCodeService(): com.coparently.app.data.remote.firebase.QRCodeService {
        return com.coparently.app.data.remote.firebase.QRCodeService()
    }
}

/**
 * Module for binding Firebase repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseRepositoryModule {

    /**
     * Provides UserRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
}

