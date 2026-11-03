package com.coparently.app.di.koin

import com.coparently.app.data.notification.WorkRequestClockScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import org.koin.dsl.module

/**
 * The clocks of requests that wait on the other parent (phase 3a): the WorkManager-backed steps of
 * a clock. The Firestore-backed clocks, `RequestDeadlineRepository`, are bound in `:shared-data`'s
 * `repositoryDataModule` (MON-24 phase 2, Task 8b). `RequestTime` and `RequestClocks` are concrete
 * and built in [dataModule].
 */
val requestClockModule = module {
    single<RequestClockScheduler> { get<WorkRequestClockScheduler>() }
}
