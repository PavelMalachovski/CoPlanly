package com.coparently.app.di

import com.coparently.app.data.notification.EventReminderScheduler
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
     * Binds the WorkManager-based reminder scheduler to the domain abstraction.
     */
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: EventReminderScheduler): ReminderScheduler
}
