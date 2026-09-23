package com.coparently.app.data.chat

import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who the chat is with, as far as this device knows *right now*.
 *
 * Three cases, because the screen and the mirror treat them differently: [Resolving] is "ask
 * again in a moment", [None] is "there is nobody to chat with", and only [Linked] names a thread.
 */
sealed interface ChatPartner {

    /** The pairing state has not answered yet (cold start, signed out, a listener recovering). */
    data object Resolving : ChatPartner

    /** This account has no co-parent. */
    data object None : ChatPartner

    /** The chat is with the co-parent whose Firebase UID is [partnerUid]. */
    data class Linked(val partnerUid: String) : ChatPartner
}

/**
 * The co-parent of the family this device is **showing**, for the chat (M-8).
 *
 * Chat used to key on [PairingRepository.observePairingState] alone, which reads the server's
 * `users/{uid}.partnerId` — `partnersOf(...)[0]`, the *first* co-parent. With two families the
 * Chat tab, its badge and the process-wide [ChatMirror] therefore stayed on the first family
 * whatever the switcher said. The selection lives in the local projection
 * [SelectedFamilySource] writes onto the signed-in Room row, which is what every other screen
 * already followed; this joins the two so `ChatViewModel` and [ChatMirror] cannot disagree about
 * which thread is "the" thread.
 *
 * **The server still decides *whether* there is a co-parent; the projection decides *which*.**
 * A projection alone cannot tell a cold start from an unpaired account (the row is missing in
 * both), and it can outlive an unpair by a sync — so [PairingState.Loading] stays
 * [ChatPartner.Resolving] and [PairingState.NotPaired] stays [ChatPartner.None] whatever the row
 * says. Only a paired account consults the projection, and falls back to the server's partner
 * when the row has not caught up yet, which is exactly the moment after a first pairing.
 *
 * No Firestore listener is added: the projection is Room's own invalidation on one row, and the
 * pairing listener is the one chat already held.
 */
@Singleton
class ChatPartnerSource @Inject constructor(
    private val userRepository: UserRepository,
    private val pairingRepository: PairingRepository,
    private val selectedFamilySource: SelectedFamilySource
) {

    /**
     * [ChatPartner], re-emitted only when it changes — a pairing snapshot re-emits on every
     * invite and profile write, and the Room row on any unrelated column.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<ChatPartner> =
        combine(
            pairingRepository.observePairingState(),
            userRepository.observeCurrentUserId().flatMapLatest { uid ->
                if (uid.isNullOrBlank()) {
                    flowOf(null)
                } else {
                    selectedFamilySource.observe(uid).map { it?.partnerUid }
                }
            }
        ) { pairing, projected -> resolve(pairing, projected) }
            .distinctUntilChanged()

    companion object {

        /**
         * The rule [observe] applies, as a pure function so it can be pinned without flows.
         *
         * @param pairing What the server says about this account.
         * @param projectedPartnerUid The co-parent of the family the device is showing, or null
         *   when the Room row carries none yet.
         */
        fun resolve(pairing: PairingState, projectedPartnerUid: String?): ChatPartner = when (pairing) {
            PairingState.Loading -> ChatPartner.Resolving
            is PairingState.NotPaired -> ChatPartner.None
            // A blank partner id is what the pairing repository falls back to when the
            // partner's profile cannot be read; with no projection either there is nothing to
            // start a conversation with.
            is PairingState.Paired ->
                (projectedPartnerUid?.takeIf { it.isNotBlank() } ?: pairing.partner.id.takeIf { it.isNotBlank() })
                    ?.let { ChatPartner.Linked(it) }
                    ?: ChatPartner.None
        }
    }
}
