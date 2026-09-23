package com.coparently.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.chat.OtherFamiliesUnreadSource
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The families this parent is in, and which one the device is showing.
 *
 * @property families Every family, each co-parent named where their profile could be read.
 *   Names are resolved only when there are at least two, because nothing shows them otherwise.
 * @property selectedFamilyId The family on screen, or null when the account is in none.
 * @property unreadFamilyIds Families whose chat moved after this parent's read mark, as
 *   [OtherFamiliesUnreadSource] reports them — read only through [hasUnread] and
 *   [otherFamilyHasUnread], which never let it speak for the family on screen or at one family.
 */
data class FamilySwitcherState(
    val families: List<FamilyOption> = emptyList(),
    val selectedFamilyId: String? = null,
    val unreadFamilyIds: Set<String> = emptySet()
) {
    /** Whether a switcher should be offered at all: **at two, not at one** (FAM-1's rule). */
    val canSwitch: Boolean get() = families.size > 1

    /** The family on screen, if it is one of [families]. */
    val selected: FamilyOption? get() = families.firstOrNull { it.familyId == selectedFamilyId }

    /** Whether any family *not* on screen has something new in its chat: the chip's dot. */
    val otherFamilyHasUnread: Boolean get() = families.any { hasUnread(it.familyId) }

    /**
     * Whether [familyId]'s row in the switcher dialog carries a dot. Never the family on
     * screen — its own badge is the Chat tab's — and never at one family, where nothing is drawn.
     */
    fun hasUnread(familyId: String): Boolean =
        canSwitch && familyId != selectedFamilyId && familyId in unreadFamilyIds
}

/**
 * State and the one action behind the family switcher — the Settings row and the top-bar chip
 * (M-8) alike, so the two cannot disagree about what is on screen or how a switch is made.
 *
 * **Observed rather than refreshed.** Settings used to reload the list when it opened; a chip
 * in a tab's top bar has no such moment, and must appear the instant a second family exists and
 * go the instant one ends. Both halves come off the signed-in parent's Room row
 * ([SelectedFamilySource.observeFamilies], [SelectedFamilySource.observe]), so observing costs no
 * Firestore listener.
 *
 * **Names are the one remote read, and they are cached here.** A co-parent's name is on their
 * own profile document, so naming the families costs one read per family. It is paid only when
 * there are two or more, only for a co-parent this instance has not named yet, and not again
 * when the list re-emits for an unrelated reason — which a tab that is left and re-entered makes
 * the ordinary case, since `WhileSubscribed` restarts the upstream each time.
 *
 * **The dot is the other exception**: one conversation-document listener per family *not* on
 * screen, owned by the shared [OtherFamiliesUnreadSource] rather than by this instance, and none
 * at all for a one-family account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilySwitcherViewModel @Inject constructor(
    private val selectedFamilySource: SelectedFamilySource,
    userRepository: UserRepository,
    otherFamiliesUnreadSource: OtherFamiliesUnreadSource
) : ViewModel() {

    /** Co-parent uid → name, for the lifetime of this instance; see the class KDoc. */
    private val names = mutableMapOf<String, String>()

    /** What the switcher shows. Empty until the signed-in row has been read. */
    val state: StateFlow<FamilySwitcherState> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(FamilySwitcherState())
            } else {
                combine(
                    selectedFamilySource.observeFamilies(uid).mapLatest(::withNames),
                    selectedFamilySource.observe(uid),
                    // Its own listeners, shared across every switcher on screen; the start value
                    // keeps the chip from waiting on Firestore to draw its name.
                    otherFamiliesUnreadSource.unreadFamilyIds.onStart { emit(emptySet()) }
                ) { families, selected, unread ->
                    FamilySwitcherState(families, selected?.familyId, unread)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FamilySwitcherState())

    /**
     * Points this device at [familyId].
     *
     * Nothing else to notify: the calendar's audience, the chat thread, the custody pair and the
     * expense query all follow the one row [SelectedFamilySource] re-points, and [state] follows
     * the same row.
     */
    fun select(familyId: String) {
        viewModelScope.launch { selectedFamilySource.select(familyId) }
    }

    private suspend fun withNames(families: List<FamilyOption>): List<FamilyOption> {
        if (families.size < 2) return families
        val unnamed = families.filter { it.partnerUid !in names }
        if (unnamed.isNotEmpty()) {
            // A blank answer is not cached: it is a read that failed as often as a profile with
            // no name, and the next emission is the retry Settings' reload used to be.
            selectedFamilySource.named(unnamed)
                .filter { it.partnerName.isNotBlank() }
                .forEach { names[it.partnerUid] = it.partnerName }
        }
        return families.map { it.copy(partnerName = names[it.partnerUid].orEmpty()) }
    }

    private companion object {
        /** Keeps the upstream warm across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
