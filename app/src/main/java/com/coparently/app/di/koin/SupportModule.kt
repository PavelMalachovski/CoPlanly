package com.coparently.app.di.koin

import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.support.WorkManagerSupportReminderScheduler
import org.koin.dsl.module

/** Child support (25 October 2026): the reminder job's scheduler, over WorkManager. */
val supportModule = module {
    factory<SupportReminderScheduler> { get<WorkManagerSupportReminderScheduler>() }
}
