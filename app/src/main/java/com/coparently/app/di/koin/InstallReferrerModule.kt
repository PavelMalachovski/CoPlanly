package com.coparently.app.di.koin

import com.coparently.app.data.pairing.PlayInstallReferrerSource
import com.coparently.app.domain.pairing.InstallReferrerSource
import org.koin.dsl.module

/**
 * The install referrer reader that carries an invitation's pairing code through a Google Play
 * install (see [PlayInstallReferrerSource]). Instrumented tests override it with a source that
 * never has a code.
 */
val installReferrerModule = module {
    single<InstallReferrerSource> { get<PlayInstallReferrerSource>() }
}
