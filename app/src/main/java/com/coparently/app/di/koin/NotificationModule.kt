package com.coparently.app.di.koin

import com.coparently.app.data.notification.ArmedReminders
import com.coparently.app.data.notification.DueReminders
import com.coparently.app.data.notification.EventReminderScheduler
import com.coparently.app.data.notification.ReminderAlarms
import com.coparently.app.data.notification.ReminderRearm
import com.coparently.app.data.notification.SystemReminderAlarms
import com.coparently.app.domain.notification.ReminderScheduler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The event reminders (CLAUDE.md item 9): the alarm-based scheduler bound to the domain
 * abstraction, the reminders' alarms bound to `AlarmManager`, the list of armed events, and what
 * the two reminder receivers resolve when a reminder is due ([DueReminders]) and after a reboot,
 * an update or a clock change ([ReminderRearm]). `EventReminderScheduler` itself is defined in
 * `dataModule`.
 */
val notificationModule = module {
    single<ReminderScheduler> { get<EventReminderScheduler>() }
    singleOf(::SystemReminderAlarms)
    single<ReminderAlarms> { get<SystemReminderAlarms>() }
    singleOf(::ArmedReminders)
    singleOf(::DueReminders)
    singleOf(::ReminderRearm)
}
