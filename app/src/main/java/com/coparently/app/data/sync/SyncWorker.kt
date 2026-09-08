package com.coparently.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coparently.app.data.crashlytics.CrashlyticsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic background synchronization.
 * Runs every 15 minutes to sync data with Firestore, whenever there is a network to sync over.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncService: SyncService,
    private val crashlyticsManager: CrashlyticsManager
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs one full sync.
     *
     * The failure branch used to read `catch (e: Exception) { Result.retry() }` — the comment
     * said "Log error and retry" and nothing logged anything. Sync could therefore fail every
     * quarter of an hour, for weeks, and leave no trace anywhere: not in logcat, not in
     * Crashlytics, and not on either parent's screen. A retry that nobody can observe is
     * indistinguishable from a sync that is working.
     */
    override suspend fun doWork(): Result {
        return try {
            requestedSinceRunStarted.set(false)
            val result = syncService.performFullSync()
            result.exceptionOrNull()?.let { cause ->
                Log.w(TAG, "Periodic sync reported a failure; will retry.", cause)
                crashlyticsManager.recordException(cause)
            }
            // A request that arrived while this run was in flight was dropped by KEEP. It is
            // evidence that something changed *after* this run read it — the co-parent's phone
            // widening its audience a second after this one pulled, say — so one more run is
            // appended rather than the request being lost until the next quarter-hour tick.
            if (requestedSinceRunStarted.getAndSet(false)) {
                enqueueFollowUp(applicationContext)
            }
            if (result.isSuccess) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            // WorkManager stopping the worker is not a failure to report.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Periodic sync threw; will retry.", e)
            crashlyticsManager.recordException(e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "periodic_data_sync"
        private const val ONE_SHOT_WORK_NAME = "immediate_data_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L

        /**
         * Set by every [syncNow], cleared as a run starts, read as it ends.
         *
         * `ExistingWorkPolicy.KEEP` drops a request that lands while the one-shot is already
         * running — which during pairing is the normal case: the accepter's phone starts a sync
         * on the transition, and the inviter's `child_info_updated` push arrives while it runs.
         * Process-wide state rather than a worker field, because the request and the run are
         * two different objects.
         */
        private val requestedSinceRunStarted = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Schedules periodic sync work.
         *
         * Constrained to [NetworkType.CONNECTED]. Without it the worker woke every fifteen
         * minutes with no network at all — on a phone in flight mode overnight, that is dozens of
         * wake-ups that could only ever fail on their first Firestore call, each one costing
         * battery and each one now also costing a Crashlytics report.
         */
        fun schedulePeriodicSync(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
        }

        /**
         * Runs one sync as soon as there is a network, without waiting for the periodic tick.
         *
         * The periodic worker is scheduled with [ExistingPeriodicWorkPolicy.UPDATE], which
         * preserves the existing period — so launching the app did not reset the fifteen-minute
         * clock, and nothing else ran a sync at all: not cold start, not foreground, not sign-in,
         * not a push. A co-parent's newly created event could therefore sit undownloaded for a
         * quarter of an hour or, under Doze, considerably longer, which is what made accepting
         * their proposed change fail with "the event for this request no longer exists".
         *
         * `ExistingWorkPolicy.KEEP` so several triggers landing together — a push arriving as the
         * user opens the app — coalesce into one run rather than queueing four.
         *
         * @param context Any context; WorkManager is resolved from the application one.
         */
        fun syncNow(context: Context) {
            requestedSinceRunStarted.set(true)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_WORK_NAME, ExistingWorkPolicy.KEEP, oneShotRequest())
        }

        /**
         * One further run after the current one, for a request KEEP dropped mid-run.
         *
         * `APPEND_OR_REPLACE` rather than KEEP: the worker enqueuing this is still running under
         * the same unique name, so KEEP would drop it exactly as it dropped the request.
         * At most one is ever appended, because the flag is consumed when it is read.
         */
        private fun enqueueFollowUp(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, oneShotRequest())
        }

        private fun oneShotRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        /**
         * Cancels periodic sync work.
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
