package com.coparently.app.di.koin

import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.CredentialProviderImpl
import org.koin.dsl.module

/** Google services: the credential provider, bound to its interface. */
val googleModule = module {
    single<CredentialProvider> { get<CredentialProviderImpl>() }
}
