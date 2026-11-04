package com.coparently.app.di.koin

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.dao.ParentingPlanDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.di.DATABASE_NAME
import com.coparently.app.di.buildCoPlanlyDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The Room database and its DAOs.
 *
 * The database is one instance for the process. Its open helper comes from `EncryptedDatabase`
 * (SEC-2), which also converts an existing plaintext file on the way. It has to happen here, in
 * the definition, rather than at application start: this is the last point that is guaranteed to
 * run before the first query opens the file, and a conversion that races the first open is a
 * conversion of a database somebody else is holding. A null factory means it could not be
 * encrypted this launch and the plaintext file is intact — see that class for why carrying on
 * beats crashing or wiping.
 *
 * The cost is a one-off copy of the whole database on the thread that first resolves a DAO,
 * which for a family's calendar is small, happens once per install, and is retried rather than
 * lost if it fails.
 *
 * Each DAO is a `factory`, as each was an unscoped Hilt provider: Room hands back its own cached
 * instance anyway.
 */
val databaseModule = module {
    single<CoPlanlyDatabase> { buildCoPlanlyDatabase(androidContext(), get(), DATABASE_NAME) }
    factory<EventDao> { get<CoPlanlyDatabase>().eventDao() }
    factory<UserDao> { get<CoPlanlyDatabase>().userDao() }
    factory<CustodyScheduleDao> { get<CoPlanlyDatabase>().custodyScheduleDao() }
    factory<ChildInfoDao> { get<CoPlanlyDatabase>().childInfoDao() }
    factory<PetDao> { get<CoPlanlyDatabase>().petDao() }
    factory<MessageDao> { get<CoPlanlyDatabase>().messageDao() }
    factory<ExpenseDao> { get<CoPlanlyDatabase>().expenseDao() }
    factory<BudgetDao> { get<CoPlanlyDatabase>().budgetDao() }
    factory<CustodyModelDao> { get<CoPlanlyDatabase>().custodyModelDao() }
    factory<ChangeRequestDao> { get<CoPlanlyDatabase>().changeRequestDao() }
    factory<ParentingPlanDao> { get<CoPlanlyDatabase>().parentingPlanDao() }
    factory<EventVersionOutboxDao> { get<CoPlanlyDatabase>().eventVersionOutboxDao() }
}
