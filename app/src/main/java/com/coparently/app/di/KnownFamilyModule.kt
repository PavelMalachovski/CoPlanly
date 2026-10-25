package com.coparently.app.di

import com.coparently.app.data.family.KnownFamilySlots
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.domain.family.FamilySlotsSource
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

    /** Each family's slots, as this phone last read them (per-family slots, 25 October 2026). */
    @Provides
    fun provideFamilySlots(impl: KnownFamilySlots): FamilySlotsSource = impl
}
