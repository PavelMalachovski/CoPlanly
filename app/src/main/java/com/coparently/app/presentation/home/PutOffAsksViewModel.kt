package com.coparently.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Which of Home's asks this person put off with "Later" (release audit R-2).
 *
 * The pop-ups used to forget "Later" with the screen, so a day-swap offer came back on every
 * visit to Home — five times in a row in the UI tour, and again after a family switch. Now a
 * put-off ask stays put off until it changes: the key it is stored under is its revision
 * (`DaySwapGroup.revision`, a proposal's time, each waiting request's id), so a new or amended ask
 * opens again. The offer itself stays where it always was, in the calendar's banner and the
 * inbox; this only decides whether Home interrupts.
 *
 * **Per family** (isolation audit, October 2026): each revision is stored under the family it was
 * put off in, so putting off one family's "2 requests" no longer silences the other family's when
 * it also has two, and a dismissal in one family no longer forgets the other family's put-offs.
 * [putOff] is the family on screen's, following the switcher.
 */
@HiltViewModel
class PutOffAsksViewModel @Inject constructor(
    private val preferences: EncryptedPreferences,
    familyViewSource: FamilyViewSource
) : ViewModel() {

    /** Every put-off ask of every family, as stored: `<familyId>/<revision>`. */
    private val stored = MutableStateFlow(read())

    /**
     * The family on screen, or blank for none. Held eagerly, because [putOff]'s writer reads it
     * when the parent taps "Later".
     */
    private val family: StateFlow<String> = familyViewSource.observe()
        .map { it.onScreen.orEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NO_FAMILY)

    /** The revisions put off in the family on screen. */
    val putOff: StateFlow<Set<String>> = combine(stored, family) { entries, familyId -> revisionsOf(entries, familyId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Puts [revisions] off in the family on screen and forgets every revision of that family no
     * longer in [waiting] — an ask that was answered, withdrawn or changed will never be shown under
     * that revision again. The other families' put-offs are kept as they are; an entry from before
     * revisions named their family is dropped.
     *
     * @param revisions The keys of the ask being put off ([HomeAsk.keys])
     * @param waiting The revisions of every ask still waiting on this parent in the family on screen
     */
    fun putOff(revisions: Set<String>, waiting: Set<String>) {
        val familyId = family.value
        val prefix = familyId + FAMILY_SEPARATOR
        val otherFamilies = stored.value.filter { it.contains(FAMILY_SEPARATOR) && !it.startsWith(prefix) }
        val thisFamily = (revisionsOf(stored.value, familyId) intersect waiting) + revisions
        val next = otherFamilies.toSet() + thisFamily.map { prefix + it }
        stored.value = next
        preferences.putString(PreferenceKeys.PUT_OFF_ASKS, next.joinToString(PreferenceKeys.LIST_SEPARATOR))
    }

    /** [familyId]'s revisions among the stored [entries]. */
    private fun revisionsOf(entries: Set<String>, familyId: String): Set<String> {
        val prefix = familyId + FAMILY_SEPARATOR
        return entries.filter { it.startsWith(prefix) }.mapTo(mutableSetOf()) { it.removePrefix(prefix) }
    }

    private fun read(): Set<String> =
        preferences.getString(PreferenceKeys.PUT_OFF_ASKS, null)
            ?.split(PreferenceKeys.LIST_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private companion object {
        /** What the family on screen is while there is none: an unpaired account has no asks. */
        const val NO_FAMILY = ""

        /** Between a stored entry's family and its revision; a family id holds no slash. */
        const val FAMILY_SEPARATOR = "/"
    }
}
