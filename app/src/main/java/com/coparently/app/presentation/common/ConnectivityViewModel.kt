package com.coparently.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Whether to tell the parent the phone is offline (docs/AUDIT-2026-10-design.md D-16).
 *
 * The app is offline-first — Room is the source of truth and every write waits in an outbox
 * until it can upload — and until this it said so nowhere: the chat header's "Up to date" and
 * Settings' "Synced at …" went on reporting the last success while nothing could leave the
 * phone. [ConnectivityBanner] is the one place that says it, for every screen.
 *
 * Going offline shows only after [OFFLINE_GRACE_MS], so a Wi-Fi-to-mobile handover or a lift
 * ride does not flash a banner; coming back hides it at once.
 */
@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    networkMonitor: NetworkMonitor
) : ViewModel() {

    /** True while the phone has had no validated internet for longer than the grace period. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isOffline: StateFlow<Boolean> = networkMonitor.networkStatus
        .mapLatest { online ->
            if (!online) delay(OFFLINE_GRACE_MS)
            !online
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private companion object {
        /** How long the network has to be gone before the banner says so. */
        const val OFFLINE_GRACE_MS = 2_000L

        /** Keeps the network callback registered across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
