package com.coparently.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

/**
 * When this person last opened the Requests inbox from Home's "See all", per family (November 2026,
 * board HomeG1): what Home's "N new" counts from ([unseenCount]). There is no "Recent" section on
 * Home any more; the co-parent's informational updates live in the inbox, and this keeps them from
 * going unseen.
 *
 * Each family's mark is its own key ([PreferenceKeys.HOME_REQUESTS_SEEN_PREFIX]), so one family's
 * look never hides another family's news (isolation audit, October 2026); [seenAtMillis] follows the
 * family switcher.
 */
class RequestsSeenViewModel(
    private val preferences: SecurePreferences,
    familyViewSource: FamilyViewSource
) : ViewModel() {

    /** The family on screen, or blank for none; held eagerly, because [markSeen] reads it on a tap. */
    private val family: StateFlow<String> = familyViewSource.observe()
        .map { it.onScreen.orEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NO_FAMILY)

    /** Bumped on every write, so [seenAtMillis] reads the store again. */
    private val writes = MutableStateFlow(0)

    /** When the family on screen's requests were last opened from Home, or null for never. */
    val seenAtMillis: StateFlow<Long?> = combine(family, writes) { familyId, _ -> read(familyId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Records that this parent opened the family on screen's requests [nowMillis]. Nothing without a family. */
    fun markSeen(nowMillis: Long = Clock.System.now().toEpochMilliseconds()) {
        val familyId = family.value
        if (familyId == NO_FAMILY) return
        preferences.putString(PreferenceKeys.HOME_REQUESTS_SEEN_PREFIX + familyId, nowMillis.toString())
        writes.value += 1
    }

    private fun read(familyId: String): Long? =
        if (familyId == NO_FAMILY) {
            null
        } else {
            preferences.getString(PreferenceKeys.HOME_REQUESTS_SEEN_PREFIX + familyId, null)?.toLongOrNull()
        }

    private companion object {
        /** What the family on screen is while there is none. */
        const val NO_FAMILY = ""
    }
}
