package com.coparently.app.presentation.changerequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The number on the Home tab's badge: how many requests wait for this parent's answer in the
 * family on screen (redesign, part 2a). The app opens on the Calendar, so what Home used to put in
 * front of the parent — "something waits for you" — has to be visible from the bar.
 *
 * It is exactly the number Home's Requests row shows (`inbox.waitingOnMe.count`, read from the one
 * [RequestsSource]), and zero while the requests are loading or there is no co-parent, so the bar
 * never claims a request it cannot back.
 */
class RequestsBadgeViewModel(requestsSource: RequestsSource) : ViewModel() {

    /** Requests waiting for this parent's answer; 0 until they are known. */
    val waitingOnMe: StateFlow<Int> = requestsSource.observe()
        .map { state -> (state as? RequestsState.Ready)?.inbox?.waitingOnMe?.count ?: 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
