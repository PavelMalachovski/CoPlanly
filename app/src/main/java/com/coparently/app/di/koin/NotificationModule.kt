package com.coparently.app.di.koin

import com.coparently.app.data.notification.EventReminderScheduler
import com.coparently.app.domain.notification.ReminderScheduler
import org.koin.dsl.module

/** The WorkManager-based reminder scheduler, bound to the domain abstraction. */
val notificationModule = module {
    single<ReminderScheduler> { get<EventReminderScheduler>() }
}
