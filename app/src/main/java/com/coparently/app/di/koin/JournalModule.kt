package com.coparently.app.di.koin

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.JournalDao
import org.koin.dsl.module

/**
 * The private journal's DAO (MON-22). Its repository, Room and nothing that leaves the phone, is
 * built and bound in `:shared-data`'s `repositoryDataModule` since MON-24 phase 2 (Task 8f).
 */
val journalDaoModule = module {
    factory<JournalDao> { get<CoPlanlyDatabase>().journalDao() }
}
