package com.coparently.app.di

import com.coparently.app.data.notification.EventReminderScheduler
import com.coparently.app.data.notification.ReminderAlarms
import com.coparently.app.data.notification.SystemReminderAlarms
import com.coparently.app.domain.notification.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module binding notification-related abstractions.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    /**
     * Binds the alarm-based reminder scheduler to the domain abstraction.
     */
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: EventReminderScheduler): ReminderScheduler

    /**
     * Binds the reminders' alarms to `AlarmManager`.
     */
    @Binds
    @Singleton
    abstract fun bindReminderAlarms(impl: SystemReminderAlarms): ReminderAlarms
}
