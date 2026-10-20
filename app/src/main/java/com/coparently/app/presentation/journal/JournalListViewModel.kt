package com.coparently.app.presentation.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the journal list shows. */
sealed interface JournalListState {
    /** Before the signed-in account and its entries have been read. */
    data object Loading : JournalListState

    /** The signed-in parent's entries, the day they are about newest first; empty is a state of its own. */
    data class Loaded(val entries: List<JournalEntry>) : JournalListState
}

/**
 * The private journal's list (MON-22): the signed-in parent's own entries, and delete with Undo.
 *
 * Follows the signed-in account rather than reading it once, so a sign-out while the screen is open
 * shows nothing rather than the previous account's notes.
 *
 * **Kept to the family on screen**, following the switcher: an entry stamped with the parent's other
 * live family is that family's to show (owner decision, October 2026). An entry of no family shows in
 * every family, and one of a family that has ended stays visible, as that family's other records do
 * ([com.coparently.app.data.family.FamilyView]). The export keeps its own scope (`RecordJournal`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalListViewModel @Inject constructor(
    private val repository: JournalRepository,
    userRepository: UserRepository,
    familyViewSource: FamilyViewSource
) : ViewModel() {

    /** The entries, or [JournalListState.Loading] until they have been read. */
    val state: StateFlow<JournalListState> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                combine(repository.observeEntries(uid), familyViewSource.observe()) { entries, view ->
                    entries.filter { view.shows(it.familyId, it.createdByFirebaseUid) }
                }
            }
        }
        .map<List<JournalEntry>, JournalListState> { JournalListState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), JournalListState.Loading)

    /**
     * Deletes [entry] at once. The screen keeps the entry it was handed so its Undo can [restore]
     * it — design item 8's pattern, the same one the event list uses.
     */
    fun delete(entry: JournalEntry) {
        viewModelScope.launch { repository.delete(entry.id, entry.createdByFirebaseUid) }
    }

    /** Puts a deleted [entry] back exactly as it was — id, day, text and both times. */
    fun restore(entry: JournalEntry) {
        viewModelScope.launch { repository.save(entry) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
