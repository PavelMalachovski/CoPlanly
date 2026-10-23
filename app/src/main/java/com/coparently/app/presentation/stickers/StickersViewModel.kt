package com.coparently.app.presentation.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.stickers.ParentRoles
import com.coparently.app.data.stickers.StickerStore
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the stickers look like from this phone (October 2026, owner decisions): the stickers this
 * parent received and sent in the family on screen — plus the app's, which belong to no family or
 * to this one — how their stickers address them, and the sending of one.
 *
 * Home's card, its "Send a sticker" sheet, its one question about the role and the "My stickers"
 * screen all read this one ViewModel, so they cannot disagree about what was seen.
 */
@HiltViewModel
class StickersViewModel @Inject constructor(
    private val store: StickerStore,
    private val roles: ParentRoles,
    private val familyViewSource: FamilyViewSource,
    parentsSource: ParentsSource
) : ViewModel() {

    /** The two parents of the family on screen, for naming who sent a sticker. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), Parents())

    private val familyView = familyViewSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** How this parent's stickers address them; [ParentRoleAnswer.Loading] until the server says. */
    val role: StateFlow<ParentRoleAnswer> = roles.observeMine()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), ParentRoleAnswer.Loading)

    /** The family on screen, or null when this parent is in none: nobody to send a sticker to. */
    val familyOnScreen: StateFlow<String?> = familyView.map { it?.onScreen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** The stickers this parent received, newest first, kept to the family on screen. */
    val received: StateFlow<List<Sticker>> = mine(StickerStore::observeReceived)

    /** The stickers this parent sent, newest first, kept to the family on screen. */
    val sent: StateFlow<List<Sticker>> = mine(StickerStore::observeSent)

    /** The received stickers this parent has not looked at yet; Home draws the newest. */
    val unseen: StateFlow<List<Sticker>> = received.map { list -> list.filter { it.seenAtMillis == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    private val _sending = MutableStateFlow<SendState>(SendState.Idle)

    /** Where the sticker being sent stands. */
    val sending: StateFlow<SendState> = _sending.asStateFlow()

    private val _roleSaveFailed = MutableStateFlow(false)

    /** Whether the last answer about the role could not be saved, so the question stays. */
    val roleSaveFailed: StateFlow<Boolean> = _roleSaveFailed.asStateFlow()

    private fun mine(read: StickerStore.(String) -> Flow<List<Sticker>>): StateFlow<List<Sticker>> {
        val uid = store.signedInUid()
        val all = uid?.let { store.read(it) } ?: flowOf(emptyList())
        return combine(all, familyView) { stickers, view ->
            // Another live family's stickers wait until that family is on screen (CLAUDE.md item
            // 46); the app's own name no family, or the one they were earned in.
            if (view == null) emptyList() else stickers.filter { view.shows(it.familyId) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())
    }

    /** Saves how this parent's stickers address them. */
    fun setRole(role: ParentRole) {
        viewModelScope.launch { _roleSaveFailed.value = !roles.setMine(role) }
    }

    /**
     * Sends [kind] to the co-parent of the family on screen — read afresh, never from
     * [familyOnScreen]'s value, which a screen that stopped collecting leaves behind (CLAUDE.md
     * item 17).
     */
    fun send(kind: StickerKind) {
        if (_sending.value == SendState.Sending) return
        _sending.value = SendState.Sending
        viewModelScope.launch {
            val familyId = familyViewSource.observe().first().onScreen
            val sent = familyId != null && store.send(familyId, kind)
            _sending.value = if (sent) SendState.Sent else SendState.Failed
        }
    }

    /** Forgets the last sending's outcome, when its sheet closes. */
    fun sendingShown() {
        _sending.value = SendState.Idle
    }

    /** Records that this parent saw [sticker]: Home stops drawing it. */
    fun markSeen(sticker: Sticker) {
        viewModelScope.launch { store.markSeen(sticker) }
    }

    /** Where a sticker being sent stands. */
    sealed interface SendState {
        /** Nothing is being sent. */
        data object Idle : SendState

        /** Waiting for the server. */
        data object Sending : SendState

        /** Stored; the co-parent finds it on Home. */
        data object Sent : SendState

        /** Not stored; nothing was sent. */
        data object Failed : SendState
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
