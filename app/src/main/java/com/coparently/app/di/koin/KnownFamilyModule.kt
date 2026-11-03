package com.coparently.app.di.koin

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.KnownFamilyDao
import org.koin.dsl.module

/**
 * The `known_families` table's DAO (schema 46). Each family's slots as this phone last read them
 * (per-family slots, 25 October 2026) are bound in `:shared-data`'s `familyDataModule` since
 * MON-24 phase 2, Task 8c.
 */
val knownFamilyModule = module {
    factory<KnownFamilyDao> { get<CoPlanlyDatabase>().knownFamilyDao() }
}
