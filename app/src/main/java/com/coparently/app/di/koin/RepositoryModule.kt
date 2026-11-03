package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.repository.CalendarFeedRepositoryImpl
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.FriendRepositoryImpl
import com.coparently.app.data.repository.GuestRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PreferencesRepositoryImpl
import com.coparently.app.data.repository.ProfessionalRepositoryImpl
import com.coparently.app.domain.repository.CalendarFeedRepository
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.domain.repository.GuestRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import org.koin.dsl.module

/**
 * The repositories, each bound to its domain interface. The implementations are built in
 * [dataModule]; a binding here resolves the same single instance. The repositories that moved to
 * `:shared-data` are built and bound there, in `repositoryDataModule` (MON-24 phase 2, Task 8b).
 */
val repositoryModule = module {
    single<EventRepository> { get<EventRepositoryImpl>() }
    single<ChildInfoRepository> { get<ChildInfoRepositoryImpl>() }
    single<PreferencesRepository> { get<PreferencesRepositoryImpl>() }
    single<ExpenseRepository> { get<ExpenseRepositoryImpl>() }
    // Medical, pet, receipt and event photographs, all under family-keyed paths and read without
    // download URLs (L-4).
    single<RecordPhotoStorage> { get<FirebaseImageStorage>() }
    // The trusted third person (item 16), bound apart from GuestRepository because a friend and a
    // guest open different things.
    single<FriendRepository> { get<FriendRepositoryImpl>() }
    // The read-only calendar links (MON-17).
    single<CalendarFeedRepository> { get<CalendarFeedRepositoryImpl>() }
    // MON-18's two-consent, expiring professional access, bound apart from FriendRepository
    // because the two are admitted by different rules.
    single<ProfessionalRepository> { get<ProfessionalRepositoryImpl>() }
    single<PetRepository> { get<PetRepositoryImpl>() }
    single<PairingRepository> { get<PairingRepositoryImpl>() }
    single<GuestRepository> { get<GuestRepositoryImpl>() }
}
