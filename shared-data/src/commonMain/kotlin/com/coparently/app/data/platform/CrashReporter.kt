package com.coparently.app.data.platform

/**
 * Reports a failure the app recovered from (MON-24 phase 2, Task 6).
 *
 * On Android it is `CrashlyticsManager`, which hands the error to Crashlytics; whether Crashlytics
 * keeps or sends anything is decided by `TelemetryConsentApplier` alone (CLAUDE.md, REL-5), never
 * here. On the iPhone it is `IosCrashReporter`, which collects nothing: there is no consent screen
 * there yet, and reporting without one is not an option (phase 4 decides).
 */
interface CrashReporter {

    /** Reports [error], a failure the caller has already handled. */
    fun record(error: Throwable)
}
