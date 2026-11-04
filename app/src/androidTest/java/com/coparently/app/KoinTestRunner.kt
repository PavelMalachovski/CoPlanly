package com.coparently.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * The instrumentation runner: runs every test in [KoinTestApplication] instead of the manifest's
 * `CoPlanlyApplication`.
 *
 * `CoPlanlyApplication.onCreate` starts the Koin graph with the real Firebase SDKs, the chat
 * mirror, the telemetry applier and WorkManager's sync schedule; none of that may run in a test.
 * Each test that needs the graph starts it itself, with its overrides, through `KoinTestRule`.
 */
class KoinTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, KoinTestApplication::class.java.name, context)
    }
}

/**
 * The application instrumented tests run in: a plain [Application] that starts nothing — no Koin
 * graph, no Firebase, no WorkManager configuration (tests that need WorkManager initialise it with
 * `WorkManagerTestInitHelper`).
 *
 * It must stay free of Koin, too: the `upgrade` job runs this test APK inside the *base* build's
 * process, whose classes may not include Koin at all.
 */
class KoinTestApplication : Application()
