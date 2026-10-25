package com.coparently.app.presentation.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Family → Earlier families: the families this parent was in and is no longer, each with its
 * records, read-only (owner decision, 25 October 2026).
 *
 * Reads every family's records — the one place besides the export that does — and keeps only the
 * ended ones ([EarlierFamilies.of]). Null until the first answer, so the screen never draws "none"
 * before Room has spoken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList") // one source per kind of record an ended family left
class EarlierFamiliesViewModel @Inject constructor(
    eventRepository: EventRepository,
    expenseRepository: ExpenseRepository,
    childInfoRepository: ChildInfoRepository,
    petRepository: PetRepository,
    journalRepository: JournalRepository,
    knownFamilyDao: KnownFamilyDao,
    familyViewSource: FamilyViewSource,
    userRepository: UserRepository
) : ViewModel() {

    private val journal = userRepository.observeCurrentUserId()
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else journalRepository.observeEntries(uid) }

    private val records = combine(
        eventRepository.getAllEventsInEveryFamily(),
        expenseRepository.getAllExpensesInEveryFamily(),
        childInfoRepository.getAllChildInfoInEveryFamily(),
        petRepository.getAllPetsInEveryFamily(),
        journal
    ) { events, expenses, children, pets, entries ->
        EveryFamilyRecords(events, expenses, children, pets, entries)
    }

    /** The ended families, newest first; null until read. */
    val families: StateFlow<List<EarlierFamily>?> = combine(
        records,
        knownFamilyDao.observeAll(),
        familyViewSource.observe()
    ) { all, known, view -> EarlierFamilies.of(view, all, known) }
        .map<List<EarlierFamily>, List<EarlierFamily>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
