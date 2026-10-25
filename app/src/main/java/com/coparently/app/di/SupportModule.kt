package com.coparently.app.di

import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.support.WorkManagerSupportReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Child support (25 October 2026): the reminder job's scheduler. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SupportModule {

    /** The reminder job over WorkManager. */
    @Binds
    abstract fun bindSupportReminderScheduler(impl: WorkManagerSupportReminderScheduler): SupportReminderScheduler
}
