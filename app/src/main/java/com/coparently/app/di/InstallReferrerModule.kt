package com.coparently.app.di

import com.coparently.app.data.pairing.PlayInstallReferrerSource
import com.coparently.app.domain.pairing.InstallReferrerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing the install referrer reader that carries an invitation's pairing
 * code through a Google Play install (see [PlayInstallReferrerSource]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstallReferrerModule {

    /** Provides the Play install referrer reader. */
    @Binds
    @Singleton
    abstract fun bindInstallReferrerSource(source: PlayInstallReferrerSource): InstallReferrerSource
}
