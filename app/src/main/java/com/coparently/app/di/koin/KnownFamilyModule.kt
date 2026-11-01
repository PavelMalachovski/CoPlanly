package com.coparently.app.di.koin

import com.coparently.app.data.family.KnownFamilySlots
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.domain.family.FamilySlotsSource
import org.koin.dsl.module

/**
 * The `known_families` table's DAO (schema 46), and each family's slots as this phone last read
 * them (per-family slots, 25 October 2026).
 */
val knownFamilyModule = module {
    factory<KnownFamilyDao> { get<CoPlanlyDatabase>().knownFamilyDao() }
    factory<FamilySlotsSource> { get<KnownFamilySlots>() }
}
