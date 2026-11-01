package com.coparently.app.di.koin

import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.error.ErrorHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The domain classes still in the app module (MON-24's stay list) that the graph builds from
 * their constructors. The event use cases have their own module, [useCaseModule].
 */
val domainModule = module {
    singleOf(::ActivityAnnouncer)
    singleOf(::ErrorHandler)
}
