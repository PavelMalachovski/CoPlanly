package com.coparently.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for CoParently.
 * Marks the application for Hilt dependency injection and initializes Firebase services.
 *
 * @see HiltAndroidApp
 */
@HiltAndroidApp
class CoParentlyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Provides WorkManager configuration with HiltWorkerFactory.
     * This enables dependency injection in WorkManager workers.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        val firebaseApp = FirebaseApp.initializeApp(this)
        if (firebaseApp != null) {
            android.util.Log.d("CoParentlyApplication", "Firebase initialized successfully")
            android.util.Log.d("CoParentlyApplication", "Project ID: ${firebaseApp.options.projectId}")
            android.util.Log.d("CoParentlyApplication", "API Key: ${firebaseApp.options.apiKey?.take(10)}...")
        } else {
            android.util.Log.e("CoParentlyApplication", "Firebase initialization failed")
        }

        // Enable Crashlytics collection
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // Schedule periodic background sync
        // Note: WorkManager is initialized via Hilt, so we can schedule work here
        com.coparently.app.data.sync.SyncWorker.schedulePeriodicSync(this)
    }
}

