package com.coparently.app.di.koin

import com.coparently.app.domain.error.ErrorHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The domain classes still in the app module (MON-24's stay list) that the graph builds from
 * their constructors. The event use cases have their own module, [useCaseModule];
 * `ActivityAnnouncer` moved to `:shared-data`'s `repositoryDataModule` (MON-24 phase 2, Task 8c).
 */
val domainModule = module {
    singleOf(::ErrorHandler)
}
