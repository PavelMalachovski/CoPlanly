package com.coparently.app.di.koin

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.repository.JournalRepositoryImpl
import com.coparently.app.domain.repository.JournalRepository
import org.koin.dsl.module

/** The private journal's DAO (MON-22). */
val journalDaoModule = module {
    factory<JournalDao> { get<CoPlanlyDatabase>().journalDao() }
}

/** The private journal's repository (MON-22): Room, and nothing that leaves the phone. */
val journalModule = module {
    single<JournalRepository> { get<JournalRepositoryImpl>() }
}
