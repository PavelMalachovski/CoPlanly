package com.coparently.app.di.koin

import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.sync.WorkManagerSyncRequester
import org.koin.dsl.module

/** The sync plumbing that is not a repository. */
val syncModule = module {
    // The one way to ask for an immediate sync from code that holds no `Context`.
    single<SyncRequester> { get<WorkManagerSyncRequester>() }
}
