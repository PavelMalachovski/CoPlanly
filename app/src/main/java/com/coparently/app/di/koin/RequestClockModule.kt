package com.coparently.app.di.koin

import com.coparently.app.data.notification.WorkRequestClockScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import org.koin.dsl.module

/**
 * The clocks of requests that wait on the other parent (phase 3a): cancelling the WorkManager jobs
 * an older build queued for a clock. No step runs on this phone since iPhone phase 4 part C: the
 * reminders, the deadline notice and the end are the server's. The Firestore-backed clocks,
 * `RequestDeadlineRepository`, are bound in `:shared-data`'s `repositoryDataModule` (MON-24 phase 2,
 * Task 8b), and the clocks' time, keeper, records and actions in its `requestsDataModule` (Task 8h).
 */
val requestClockModule = module {
    single<RequestClockScheduler> { get<WorkRequestClockScheduler>() }
}
