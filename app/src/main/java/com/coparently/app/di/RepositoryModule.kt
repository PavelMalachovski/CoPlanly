package com.coparently.app.di

import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.repository.BudgetRepositoryImpl
import com.coparently.app.data.repository.CalendarFeedRepositoryImpl
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChatSearchRepositoryImpl
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.FriendRepositoryImpl
import com.coparently.app.data.repository.GuestRepositoryImpl
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PreferencesRepositoryImpl
import com.coparently.app.data.repository.ProfessionalRepositoryImpl
import com.coparently.app.domain.repository.BudgetRepository
import com.coparently.app.domain.repository.CalendarFeedRepository
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.domain.repository.GuestRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Provides EventRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindEventRepository(
        eventRepositoryImpl: EventRepositoryImpl
    ): EventRepository

    /**
     * Provides ChildInfoRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindChildInfoRepository(
        childInfoRepositoryImpl: ChildInfoRepositoryImpl
    ): ChildInfoRepository

    /**
     * Provides PreferencesRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository

    /**
     * Provides MessageRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    /**
     * Provides the Room-only chat search (MON-15).
     */
    @Binds
    @Singleton
    abstract fun bindChatSearchRepository(
        chatSearchRepositoryImpl: ChatSearchRepositoryImpl
    ): ChatSearchRepository

    /**
     * Provides ExpenseRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        expenseRepositoryImpl: ExpenseRepositoryImpl
    ): ExpenseRepository

    /**
     * Provides BudgetRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindBudgetRepository(
        budgetRepositoryImpl: BudgetRepositoryImpl
    ): BudgetRepository

    /**
     * Provides ChangeRequestRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindChangeRequestRepository(
        changeRequestRepositoryImpl: ChangeRequestRepositoryImpl
    ): ChangeRequestRepository

    /**
     * Provides the record-photo storage (L-4): medical, pet, receipt and event photographs, all
     * under family-keyed paths and read without download URLs.
     */
    @Binds
    @Singleton
    abstract fun bindRecordPhotoStorage(
        firebaseImageStorage: FirebaseImageStorage
    ): RecordPhotoStorage

    /**
     * Provides FriendRepository implementation — the trusted third person (item 16), bound
     * separately from GuestRepository because a friend and a guest open different things.
     */
    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        friendRepositoryImpl: FriendRepositoryImpl
    ): FriendRepository

    /**
     * Provides CalendarFeedRepository implementation — the read-only calendar links (MON-17).
     */
    @Binds
    @Singleton
    abstract fun bindCalendarFeedRepository(
        calendarFeedRepositoryImpl: CalendarFeedRepositoryImpl
    ): CalendarFeedRepository

    /**
     * Provides ProfessionalRepository — MON-18's two-consent, expiring professional access,
     * bound apart from FriendRepository because the two are admitted by different rules.
     */
    @Binds
    @Singleton
    abstract fun bindProfessionalRepository(
        professionalRepositoryImpl: ProfessionalRepositoryImpl
    ): ProfessionalRepository

    /**
     * Provides PetRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindPetRepository(
        petRepositoryImpl: PetRepositoryImpl
    ): PetRepository

    /** Binds the Firestore-backed pairing repository. */
    @Binds
    @Singleton
    abstract fun bindPairingRepository(
        pairingRepositoryImpl: PairingRepositoryImpl
    ): PairingRepository

    /** Binds the Firestore-backed guest-access repository. */
    @Binds
    @Singleton
    abstract fun bindGuestRepository(
        guestRepositoryImpl: GuestRepositoryImpl
    ): GuestRepository
}
