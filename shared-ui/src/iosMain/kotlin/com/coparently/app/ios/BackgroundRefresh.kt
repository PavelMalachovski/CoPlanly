package com.coparently.app.ios

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The iPhone's background refresh (phase 4 part D, spec §7.4): what a `BGAppRefreshTask` runs, as a
 * plain suspend function a test calls, since the simulator starts such a task only from a debugger.
 *
 * 1. **A locked phone, or nobody signed in, ends it at once, reporting success**, before any step
 *    touches the database: the database keeps `NSFileProtectionComplete` (owner decision (b) of 18
 *    November 2026, CLAUDE.md item 20), so it cannot be read while [protectedDataAvailable] is
 *    false, and the widget's week of lines covers the locked hours.
 * 2. **The sync** ([sync]), through the one `ForegroundSyncRunner`, so a foreground run and a
 *    background one never overlap; it returns once a run that started after the request has ended,
 *    which also ran the runner's `afterSync`: the Google Calendar import, the daily school import
 *    (`DailySchoolImport`) and the local reminders planned again.
 * 3. **The widget's snapshot** ([writeWidget]), written however the sync went.
 *
 * A step's failure is logged and does not stop the next. A cancellation (iOS's expiration handler)
 * is never caught: it ends the run, and the task then reports no success ([startBackgroundRun]).
 *
 * @property protectedDataAvailable Whether the phone's protected data can be read now
 *   (`UIApplication.isProtectedDataAvailable`), read on the main thread.
 * @property signedIn Whether an account is signed in now.
 * @property sync One sync, awaited to its end.
 * @property writeWidget Writes the Today widget's snapshot.
 */
class BackgroundRefresh(
    private val protectedDataAvailable: () -> Boolean,
    private val signedIn: () -> Boolean,
    private val sync: suspend () -> Unit,
    private val writeWidget: suspend () -> Unit,
) {

    /**
     * Runs the chain.
     *
     * @return true when it ran to its end, or ended at once by rule (locked, signed out).
     * @throws CancellationException when the run was cancelled.
     */
    suspend fun run(): Boolean {
        if (!protectedDataAvailable() || !signedIn()) return true
        step("The background sync failed") { sync() }
        step("The background refresh could not write the widget") { writeWidget() }
        return true
    }

    private suspend fun step(failure: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { e ->
            if (e is CancellationException) throw e
            logger.w(TAG, failure, e)
        }
    }

    private companion object {
        const val TAG = "BackgroundRefresh"
    }
}

/**
 * The background task's scheduling, which the app's Swift side implements over `BGTaskScheduler`
 * (`BackgroundRefreshBridge`): one request for `app.coplanly.refresh`, never before 30 minutes from
 * now.
 */
interface BackgroundScheduling {
    /** Submits the next request, replacing a pending one; a refusal is logged by the Swift side. */
    fun schedule()

    /** Cancels the pending request (sign-out, account deletion). */
    fun cancel()
}

/**
 * One background run in flight, as `IosApp.runBackgroundRefresh` hands it to the Swift side, whose
 * task's expiration handler calls [cancel].
 */
class BackgroundRun internal constructor(private val job: Job?) {
    /** Cancels the run; its completion then reports no success. */
    fun cancel() {
        job?.cancel()
    }
}

/**
 * Launches [run] in [scope] and calls [finished] exactly once when it ends: true only when it
 * returned true; false when it returned false, threw, or was cancelled, even before it started.
 */
internal fun startBackgroundRun(
    scope: CoroutineScope,
    run: suspend () -> Boolean,
    finished: (Boolean) -> Unit,
): BackgroundRun {
    var succeeded = false
    val job = scope.launch {
        runCatching { run() }
            .onSuccess { succeeded = it }
            .onFailure { e -> if (e !is CancellationException) logger.w("BackgroundRefresh", "The run threw", e) }
    }
    // Called once whatever happened, a cancellation before the first line included.
    job.invokeOnCompletion { cause -> finished(cause == null && succeeded) }
    return BackgroundRun(job)
}
