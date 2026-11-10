package com.coparently.app.di.koin

import com.coparently.app.data.notification.ReminderWorker
import com.coparently.app.data.requests.RequestClockWorker
import com.coparently.app.data.school.SchoolImportWorker
import com.coparently.app.data.support.SupportReminderWorker
import com.coparently.app.data.sync.SyncWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module

/**
 * The WorkManager workers. `KoinWorkerFactory` (set by `CoPlanlyApplication`) finds each by its
 * class name and hands it the `WorkerParameters` of the run; the `Context` is the application's.
 */
val workerModule = module {
    workerOf(::ReminderWorker)
    workerOf(::RequestClockWorker)
    workerOf(::SchoolImportWorker)
    workerOf(::SupportReminderWorker)
    workerOf(::SyncWorker)
}
