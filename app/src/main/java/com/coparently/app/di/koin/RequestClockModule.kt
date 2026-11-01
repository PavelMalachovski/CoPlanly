package com.coparently.app.di.koin

import com.coparently.app.data.notification.WorkRequestClockScheduler
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import org.koin.dsl.module

/**
 * The clocks of requests that wait on the other parent (phase 3a): the Firestore-backed clocks
 * and the WorkManager-backed steps of a clock. `RequestTime` and `RequestClocks` are concrete and
 * built in [dataModule].
 */
val requestClockModule = module {
    single<RequestDeadlineRepository> { get<RequestDeadlineRepositoryImpl>() }
    single<RequestClockScheduler> { get<WorkRequestClockScheduler>() }
}
