package com.coparently.app.presentation.home

import androidx.lifecycle.ViewModel
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Which of Home's asks this person put off with "Later" (release audit R-2).
 *
 * The pop-ups used to forget "Later" with the screen, so a day-swap offer came back on every
 * visit to Home — five times in a row in the UI tour, and again after a family switch. Now a
 * put-off ask stays put off until it changes: the key it is stored under is its revision
 * (`DaySwapGroup.revision`, a proposal's time, the number of requests), so a new or amended ask
 * opens again. The offer itself stays where it always was, in the calendar's banner and the
 * inbox; this only decides whether Home interrupts.
 */
@HiltViewModel
class PutOffAsksViewModel @Inject constructor(
    private val preferences: EncryptedPreferences
) : ViewModel() {

    private val _putOff = MutableStateFlow(read())

    /** The revisions put off, as stored. */
    val putOff: StateFlow<Set<String>> = _putOff.asStateFlow()

    /**
     * Puts [revision] off and forgets every stored revision that is no longer in [waiting] — an
     * ask that was answered, withdrawn or changed will never be shown under that revision again.
     *
     * @param revision The ask being put off
     * @param waiting The revisions of every ask still waiting on this parent
     */
    fun putOff(revision: String, waiting: Set<String>) {
        val next = (_putOff.value intersect waiting) + revision
        _putOff.value = next
        preferences.putString(PreferenceKeys.PUT_OFF_ASKS, next.joinToString(PreferenceKeys.LIST_SEPARATOR))
    }

    private fun read(): Set<String> =
        preferences.getString(PreferenceKeys.PUT_OFF_ASKS, null)
            ?.split(PreferenceKeys.LIST_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
}
