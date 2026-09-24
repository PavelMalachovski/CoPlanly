package com.coparently.app.di

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.repository.JournalRepositoryImpl
import com.coparently.app.domain.repository.JournalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The private journal's DAO (MON-22).
 *
 * A module of its own rather than another function in `DatabaseModule`, which is already over
 * detekt's function limit in the baseline; adding to a baselined finding is still adding to it.
 */
@Module
@InstallIn(SingletonComponent::class)
object JournalDaoModule {

    /** The journal table's DAO. */
    @Provides
    fun provideJournalDao(database: CoPlanlyDatabase): JournalDao = database.journalDao()
}

/** Binds the private journal's repository (MON-22): Room, and nothing that leaves the phone. */
@Module
@InstallIn(SingletonComponent::class)
abstract class JournalModule {

    /** The Room-only repository. */
    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository
}
