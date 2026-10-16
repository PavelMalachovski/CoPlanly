package com.coparently.app.di

import com.coparently.app.data.notification.WorkRequestClockScheduler
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the clocks of requests that wait on the other parent (phase 3a).
 *
 * `RequestTime` and `RequestClocks` need no binding: they are concrete, singleton and
 * constructor-injected.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RequestClockModule {

    /** The Firestore-backed clocks. */
    @Binds
    @Singleton
    abstract fun bindRequestDeadlineRepository(impl: RequestDeadlineRepositoryImpl): RequestDeadlineRepository

    /** The WorkManager-backed steps of a clock. */
    @Binds
    @Singleton
    abstract fun bindRequestClockScheduler(impl: WorkRequestClockScheduler): RequestClockScheduler
}
