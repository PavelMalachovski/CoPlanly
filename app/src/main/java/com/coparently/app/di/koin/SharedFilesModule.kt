package com.coparently.app.di.koin

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import org.koin.dsl.module

/** The vault index cache's DAO (MON-23, schema 43). */
val familyDocumentCacheDaoModule = module {
    factory<FamilyDocumentCacheDao> { get<CoPlanlyDatabase>().familyDocumentCacheDao() }
}
