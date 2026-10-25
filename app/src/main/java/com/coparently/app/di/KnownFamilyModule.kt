package com.coparently.app.di

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.KnownFamilyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The `known_families` table's DAO (schema 46). Its own module for the reason
 * `FamilyDocumentCacheDaoModule` gives: `DatabaseModule` is over detekt's function limit in the
 * baseline.
 */
@Module
@InstallIn(SingletonComponent::class)
object KnownFamilyModule {

    /** The `known_families` table's DAO. */
    @Provides
    fun provideKnownFamilyDao(database: CoPlanlyDatabase): KnownFamilyDao = database.knownFamilyDao()
}
