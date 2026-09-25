package com.coparently.app.di

import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.WorkManagerSchoolImportScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The HTTP client the school import (MON-8) talks to school servers with.
 *
 * Qualified so it can never be picked up by something else by accident: it carries no
 * interceptor, no cache and no cookie jar, and the requests it makes carry a child's school
 * credentials.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SchoolHttpClient

/** Hilt module for the school import (MON-8). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SchoolModule {

    /** The daily import runs on WorkManager. */
    @Binds
    @Singleton
    abstract fun bindSchoolImportScheduler(impl: WorkManagerSchoolImportScheduler): SchoolImportScheduler

    companion object {
        /** A school server that has not answered in this long is treated as unreachable. */
        private const val TIMEOUT_SECONDS = 20L

        /** The client for school servers and Bakaláři's directory. */
        @Provides
        @Singleton
        @SchoolHttpClient
        fun provideSchoolHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
    }
}
