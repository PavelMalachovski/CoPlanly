package com.coparently.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.feed.CalendarFeedLimitException
import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed
import com.coparently.app.domain.repository.CalendarFeedRepository
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The calendar-feed screen's state (MON-17).
 *
 * @property familyId The family the screen is about — the one the device is showing — or null
 *   when the account is in none, in which case there is nothing a link could serve.
 * @property links This parent's live links **to that family**, newest first. Links into another
 *   family are listed after switching to it, the rule every other family-scoped screen follows.
 * @property isLoading The first list has not come back yet.
 * @property isBusy A create or revoke is in flight; the controls wait for it.
 * @property created The link just made, whose URL the server will never return again, or null.
 *   The screen offers it to share and copy until the parent dismisses it.
 * @property message A one-off outcome for the snackbar, or null.
 */
data class CalendarFeedUiState(
    val familyId: String? = null,
    val links: List<CalendarFeedLink> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val created: CreatedCalendarFeed? = null,
    val message: UiText? = null
)

/**
 * Making, listing and revoking the read-only calendar links a parent gives an iPhone (MON-17).
 *
 * Everything goes through [CalendarFeedRepository], which is to say through Cloud Functions:
 * nothing about a link is stored on the device, and the URL exists here only in [CalendarFeedUiState.created]
 * — in memory, until the parent dismisses it or leaves the screen.
 */
@HiltViewModel
class CalendarFeedViewModel @Inject constructor(
    private val repository: CalendarFeedRepository,
    private val selectedFamilySource: SelectedFamilySource
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarFeedUiState())

    /** What the screen shows. */
    val state: StateFlow<CalendarFeedUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the family on screen and its links. A failed list keeps what was shown. */
    fun refresh() {
        viewModelScope.launch {
            val familyId = selectedFamilySource.selected()?.familyId
            if (familyId == null) {
                _state.update { it.copy(familyId = null, links = emptyList(), isLoading = false) }
                return@launch
            }
            repository.list()
                .onSuccess { links ->
                    _state.update { current ->
                        current.copy(
                            familyId = familyId,
                            links = links.filter { it.familyId == familyId },
                            isLoading = false
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            familyId = familyId,
                            isLoading = false,
                            message = UiText.Res(R.string.calendar_feed_load_failed)
                        )
                    }
                }
        }
    }

    /**
     * Makes a new link to the family on screen.
     *
     * @param language The app language, resolved by the screen from its own configuration — a
     *   ViewModel has no `Context` and must not be given one.
     */
    fun create(language: String) {
        val familyId = _state.value.familyId ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            repository.create(familyId, language)
                .onSuccess { created ->
                    _state.update { it.copy(isBusy = false, created = created) }
                    refresh()
                }
                .onFailure { error ->
                    val message = if (error is CalendarFeedLimitException) {
                        R.string.calendar_feed_limit_reached
                    } else {
                        R.string.calendar_feed_create_failed
                    }
                    _state.update { it.copy(isBusy = false, message = UiText.Res(message)) }
                }
        }
    }

    /** Ends the link [feedId]. It leaves the list only once the server has confirmed. */
    fun revoke(feedId: String) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            repository.revoke(feedId)
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            isBusy = false,
                            links = current.links.filterNot { it.feedId == feedId },
                            // A revoked link must not stay on screen ready to be shared.
                            created = current.created?.takeUnless { it.feedId == feedId },
                            message = UiText.Res(R.string.calendar_feed_revoked)
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(isBusy = false, message = UiText.Res(R.string.calendar_feed_revoke_failed))
                    }
                }
        }
    }

    /** Forgets the just-made URL. It cannot be fetched again; the link itself keeps working. */
    fun dismissCreated() {
        _state.update { it.copy(created = null) }
    }

    /** The snackbar has shown [CalendarFeedUiState.message]. */
    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
