package com.coparently.app.di

import com.coparently.app.e2e.UiTourSeed
import com.coparently.app.testing.SignedInSession
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * The test-only classes the graph builds: a new instance each time, as each was an unscoped
 * `@Inject` class under Hilt.
 */
val instrumentedTestModule = module {
    factoryOf(::SignedInSession)
    factoryOf(::UiTourSeed)
}

/**
 * What `KoinTestRule` loads after the app's modules, every definition replacing the app's of the
 * same type — what Hilt's `@TestInstallIn` modules did, for every instrumented test at once.
 */
val instrumentedTestOverrides: List<Module> = listOf(
    fakeFirebaseModule,
    fakeInstallReferrerModule,
    instrumentedTestModule
)
