package com.coparently.app.data.platform

/**
 * The iPhone's [CrashReporter]: collects nothing (MON-24 phase 2).
 *
 * The honest answer while that platform has no consent screen and no crash-reporting SDK; phase 4
 * decides whether it gets both. The error is still written to the log, which stays on the phone.
 */
object IosCrashReporter : CrashReporter {
    override fun record(error: Throwable) {
        logger.w(TAG, "Recovered from a failure (not reported anywhere)", error)
    }

    private const val TAG = "CrashReporter"
}

/** The iPhone's [Analytics]: collects nothing, for [IosCrashReporter]'s reason. */
object IosAnalytics : Analytics {
    override fun log(event: String, params: Map<String, Any>) = Unit
}
