package com.coparently.app

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for CoParently.
 * Marks the application for Hilt dependency injection and initializes Firebase services.
 *
 * @see HiltAndroidApp
 */
@HiltAndroidApp
class CoParentlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable Crashlytics collection
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }
}

