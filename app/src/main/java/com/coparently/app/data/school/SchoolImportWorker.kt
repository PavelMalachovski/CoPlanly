package com.coparently.app.data.school

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the daily school import (MON-8) for every connection of the signed-in account.
 *
 * A connection's failure is recorded on the connection — "needs password" or "error" — and never
 * fails the work: WorkManager's retry would only repeat a refused sign-in. When the account has no
 * connection left, the job cancels itself, so a phone that disconnected its last school is not
 * woken daily for nothing.
 */
@HiltWorker
class SchoolImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val importer: SchoolImporter,
    private val scheduler: SchoolImportScheduler
) : CoroutineWorker(context, workerParams) {

    /** Updates every connection once. */
    override suspend fun doWork(): Result {
        return try {
            if (importer.syncAll() == 0) scheduler.cancel()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Each connection's outcome is already on the connection; this is the unexpected rest.
            Log.e(TAG, "The school import failed unexpectedly", e)
            Result.success()
        }
    }

    private companion object {
        const val TAG = "SchoolImportWorker"
    }
}

/** Starts and stops the daily school import. An interface so the connect flow can be tested. */
interface SchoolImportScheduler {
    /** Makes sure the daily import runs; a no-op when it is already scheduled. */
    fun schedule()

    /** Stops the daily import. */
    fun cancel()
}

/**
 * [SchoolImportScheduler] over WorkManager: unique periodic work named [WORK_NAME], once a day,
 * only with a network, kept rather than replaced so connecting a second school does not move the
 * first one's clock.
 */
@Singleton
class WorkManagerSchoolImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SchoolImportScheduler {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<SchoolImportWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        /** The unique work's name. Part of what WorkManager persists; never renamed. */
        const val WORK_NAME = "school_import_daily"
        private const val INTERVAL_DAYS = 1L
    }
}
