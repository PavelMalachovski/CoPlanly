package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.PreferencesRepositoryImpl
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import org.koin.dsl.module

/**
 * The repositories, each bound to its domain interface. The implementations are built in
 * [dataModule]; a binding here resolves the same single instance. The repositories that moved to
 * `:shared-data` are built and bound there, in `repositoryDataModule` (MON-24 phase 2, Task 8b).
 */
val repositoryModule = module {
    single<PreferencesRepository> { get<PreferencesRepositoryImpl>() }
    single<ExpenseRepository> { get<ExpenseRepositoryImpl>() }
    // Medical, pet, receipt and event photographs, all under family-keyed paths and read without
    // download URLs (L-4).
    single<RecordPhotoStorage> { get<FirebaseImageStorage>() }
    single<PairingRepository> { get<PairingRepositoryImpl>() }
}
