package com.coparently.app.presentation.family

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.AssignOutcome
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.family.UnassignedKind
import com.coparently.app.data.family.UnassignedRecord
import com.coparently.app.data.family.UnassignedRecords
import com.coparently.app.data.family.UnassignedSnapshot
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The groups the list draws, in order, each with the kinds it holds. */
enum class UnassignedGroup(val kinds: Set<UnassignedKind>) {
    CHILDREN_AND_PETS(setOf(UnassignedKind.CHILD, UnassignedKind.PET)),
    EVENTS(setOf(UnassignedKind.EVENT)),
    EXPENSES(setOf(UnassignedKind.EXPENSE)),
    CUSTODY(setOf(UnassignedKind.CUSTODY))
}

/**
 * What "Records without a family" shows.
 *
 * @property loading True until the first snapshot arrives, so the screen does not flash its empty
 *   state before Room has answered.
 * @property records Every record still to be answered.
 * @property families The parent's live families, each co-parent named where their profile could be
 *   read.
 * @property onScreenFamilyId The family the switcher shows.
 * @property earlierFamilies The parent's ended families, where an event, a child or a pet of an
 *   earlier household can be put away.
 */
data class UnassignedRecordsUiState(
    val loading: Boolean = true,
    val records: List<UnassignedRecord> = emptyList(),
    val families: List<FamilyOption> = emptyList(),
    val onScreenFamilyId: String? = null,
    val earlierFamilies: List<FamilyOption> = emptyList()
) {
    /** The non-empty groups, in [UnassignedGroup] order. */
    val groups: List<Pair<UnassignedGroup, List<UnassignedRecord>>>
        get() = UnassignedGroup.entries
            .map { group -> group to records.filter { it.kind in group.kinds } }
            .filter { (_, members) -> members.isNotEmpty() }

    /**
     * The families [record] may go to: any live family, except that a custody schedule goes only to
     * the family on screen, because it is submitted there.
     */
    fun choicesFor(record: UnassignedRecord): List<FamilyOption> =
        if (record.kind == UnassignedKind.CUSTODY) {
            families.filter { it.familyId == onScreenFamilyId }
        } else {
            families
        }

    /** The ended families [record] may be put away in: none for an expense or a schedule. */
    fun earlierChoicesFor(record: UnassignedRecord): List<FamilyOption> =
        if (record.kind.archivable) earlierFamilies else emptyList()

    /** The live family [familyId], named, or null. */
    fun family(familyId: String?): FamilyOption? = families.firstOrNull { it.familyId == familyId }

    /** Whether [familyId] is one of the ended families. */
    fun isEarlier(familyId: String?): Boolean = earlierFamilies.any { it.familyId == familyId }
}

/**
 * What the Settings row and Home's question need: how many records wait, and whether to ask.
 * Cheap on purpose — no co-parent is named for it.
 */
data class UnassignedSummary(val count: Int = 0, val askOnHome: Boolean = false)

/** What an answer did, said once in a snackbar. */
sealed interface UnassignedNotice {
    /**
     * An assignment to [familyId] landed as [outcome]; [putAway] when [familyId] was an ended
     * family, which the list may no longer name once its last record has gone.
     */
    data class Assigned(
        val outcome: AssignOutcome,
        val familyId: String,
        val putAway: Boolean = false
    ) : UnassignedNotice

    /** The record was kept only the parent's. */
    data object Kept : UnassignedNotice
}

/**
 * State and actions behind "Records without a family" — the screen, its row in Settings → Family,
 * and Home's one-time question (owner decision, October 2026).
 *
 * Follows the signed-in account: [UnassignedRecords] observes the parent's own rows of no family,
 * the co-parent set and every answer, so a record leaves the list the moment it takes a family or
 * is kept. The co-parents' names are the one remote read — one per co-parent, cached for this
 * instance and paid only by [state], which only the screen collects; [summary] names nobody.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UnassignedRecordsViewModel @Inject constructor(
    private val unassignedRecords: UnassignedRecords,
    private val selectedFamilySource: SelectedFamilySource,
    private val userRepository: UserRepository
) : ViewModel() {

    /** Co-parent uid → name, for the lifetime of this instance. */
    private val names = mutableMapOf<String, String>()

    private val snapshots: Flow<UnassignedSnapshot> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid -> if (uid == null) flowOf(UnassignedSnapshot()) else unassignedRecords.observe(uid) }

    /** How many records wait, and whether Home asks about them now. */
    val summary: StateFlow<UnassignedSummary> = snapshots
        .map { UnassignedSummary(count = it.records.size, askOnHome = it.askOnHome) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UnassignedSummary())

    /** The list, with the families named. */
    val state: StateFlow<UnassignedRecordsUiState> = snapshots
        .mapLatest { snapshot ->
            UnassignedRecordsUiState(
                loading = false,
                records = snapshot.records,
                // Named only while there is something to choose a family for.
                families = if (snapshot.records.isEmpty()) snapshot.families else withNames(snapshot.families),
                onScreenFamilyId = snapshot.onScreenFamilyId,
                earlierFamilies = withNames(snapshot.earlierFamilies)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UnassignedRecordsUiState())

    private val _busy = MutableStateFlow(false)

    /** True while an answer is being written; the rows do not take a second one meanwhile. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<UnassignedNotice?>(null)

    /** What the last answer did, until the screen has said it ([noticeShown]). */
    val notice: StateFlow<UnassignedNotice?> = _notice.asStateFlow()

    /** Gives [record] to [familyId]. */
    fun assign(record: UnassignedRecord, familyId: String) {
        if (_busy.value) return
        _busy.value = true
        val putAway = state.value.isEarlier(familyId)
        viewModelScope.launch {
            try {
                val uid = userRepository.getCurrentUserId() ?: return@launch
                val outcome = try {
                    unassignedRecords.assign(uid, record, familyId)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // A failed write must say so rather than end the screen's scope.
                    Log.w(TAG, "Assigning a record without a family failed", e)
                    AssignOutcome.NOT_SENT
                }
                _notice.value = UnassignedNotice.Assigned(outcome, familyId, putAway)
            } finally {
                _busy.value = false
            }
        }
    }

    /** "Keep it only mine": [record] is not asked about again. */
    fun keep(record: UnassignedRecord) {
        viewModelScope.launch {
            val uid = userRepository.getCurrentUserId() ?: return@launch
            unassignedRecords.keep(uid, record)
            _notice.value = UnassignedNotice.Kept
        }
    }

    /** Home's question was answered — "Choose" or "Later": not asked again for this co-parent set. */
    fun promptAnswered() {
        viewModelScope.launch {
            val uid = userRepository.getCurrentUserId() ?: return@launch
            unassignedRecords.markAsked(uid)
        }
    }

    /** The screen has said [notice]. */
    fun noticeShown() {
        _notice.value = null
    }

    /**
     * [families] with each co-parent named: a name a family already carries (an ended family's,
     * as this phone kept it) stays, and the rest are read once per instance.
     */
    private suspend fun withNames(families: List<FamilyOption>): List<FamilyOption> {
        val unnamed = families.filter { it.partnerName.isBlank() && it.partnerUid !in names }
        if (unnamed.isNotEmpty()) {
            // A blank answer is not cached: a failed read looks like a profile with no name, and the
            // next emission is the retry.
            selectedFamilySource.named(unnamed)
                .filter { it.partnerName.isNotBlank() }
                .forEach { names[it.partnerUid] = it.partnerName }
        }
        return families.map { it.copy(partnerName = it.partnerName.ifBlank { names[it.partnerUid].orEmpty() }) }
    }

    private companion object {
        const val TAG = "UnassignedRecords"

        /** Keeps the upstream warm across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
