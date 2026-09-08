package com.coparently.app.di

import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.sync.WorkManagerSyncRequester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the sync plumbing that is not a repository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    /** The one way to ask for an immediate sync from code that holds no `Context`. */
    @Binds
    @Singleton
    abstract fun bindSyncRequester(impl: WorkManagerSyncRequester): SyncRequester
}
