package com.coparently.app.presentation.common

import com.coparently.app.data.chat.ChatPartner
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The co-parent of the family this device is showing (M-8), as far as it knows right now.
 *
 * Three cases for the reason [ChatPartner] has three: [Resolving] is "ask again in a moment",
 * [None] is "nobody", and only [Linked] names somebody.
 */
sealed interface CoParentOnScreen {

    /** The pairing state has not answered yet: a cold start, a signed-out device, a listener recovering. */
    data object Resolving : CoParentOnScreen

    /** This account has no co-parent, whatever a stale family projection says. */
    data object None : CoParentOnScreen

    /**
     * The family on screen is with [uid].
     *
     * @property profile Their own `users/{uid}` document, kept current, or null until it has been
     *   read. Never another co-parent's: a switch replaces the previous profile with null at once
     *   rather than leaving it under the new family until the next one arrives.
     */
    data class Linked(val uid: String, val profile: PartnerSummary?) : CoParentOnScreen
}

/**
 * [CoParentOnScreen] from the server's pairing state and the family projection, with the
 * co-parent's profile kept live — the answer every screen that names "the co-parent" shares.
 *
 * **Which co-parent is [ChatPartnerSource.resolve]'s rule**, the one the chat has followed since
 * M-8: the server decides *whether* there is one, the projection the family switcher writes onto
 * the signed-in Room row decides *which*, and the server's partner stands in while the row has
 * not caught up. Before this, the names and colours came from [PairingState.Paired.partner] alone
 * — the server's `partnerId`, which `acceptPairingInvitation` keeps on the *first* co-parent — so
 * a parent who switched to their second family was still shown the first one on the event form,
 * the calendar, the expenses and Home, over records that were, correctly, the second family's
 * (tester report #11).
 *
 * **The profile comes from wherever it is already live.** The pairing listener follows the
 * server's partner, so while the family on screen is that one, the profile is the one the pairing
 * state carries and nothing is added. Any other co-parent gets [profileOf] for as long as their
 * family stays on screen — keyed on the uid alone, so a pairing state that re-emits (an invite, the
 * first co-parent's rename) does not re-attach it, and a switch drops the previous profile at once
 * ([CoParentOnScreen.Linked.profile] null) rather than showing it under the new family for a frame.
 *
 * @param pairing The pairing state; collected once.
 * @param projectedPartnerUid The co-parent the signed-in Room row's `partnerId` names, or null —
 *   see [projectedCoParentUid].
 * @param profileOf A co-parent's live profile: `PairingRepository.observeCoParentProfile`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun coParentOnScreen(
    pairing: Flow<PairingState>,
    projectedPartnerUid: Flow<String?>,
    profileOf: (String) -> Flow<PartnerSummary>
): Flow<CoParentOnScreen> =
    combine(pairing, projectedPartnerUid) { state, projected -> profileSourceOf(state, projected) }
        .distinctUntilChanged()
        .flatMapLatest { source ->
            when (source) {
                is CoParentProfileSource.Known -> flowOf(source.answer)
                is CoParentProfileSource.Listen -> profileOf(source.uid)
                    .map<PartnerSummary, CoParentOnScreen> { CoParentOnScreen.Linked(source.uid, it) }
                    .onStart { emit(CoParentOnScreen.Linked(source.uid, profile = null)) }
            }
        }
        .distinctUntilChanged()

/**
 * The signed-in parent's Room row, matched by uid — never "the only row", for the reason
 * [Parents] gives: sign-out does not clear Room, so a device two accounts have used holds two.
 */
internal fun UserRepository.observeSignedInUser(): Flow<User?> =
    combine(observeCurrentUserId(), getAllUsers()) { uid, users ->
        uid?.let { id -> users.firstOrNull { it.id == id } }
    }

/**
 * The co-parent of the family this row is projected onto (`SelectedFamilySource`), or null when
 * it names nobody — or, as only a corrupt row would, the parent themselves.
 */
internal fun User.projectedCoParentUid(): String? =
    partnerId?.takeIf { it.isNotBlank() && it != id }

/** Where [coParentOnScreen] takes the profile from: an answer already in hand, or a listener of its own. */
private sealed interface CoParentProfileSource {

    /** Resolving, None, or the server's partner as the pairing listener already carries them. */
    data class Known(val answer: CoParentOnScreen) : CoParentProfileSource

    /** A co-parent the pairing listener does not follow, read through a listener of their own. */
    data class Listen(val uid: String) : CoParentProfileSource
}

private fun profileSourceOf(pairing: PairingState, projected: String?): CoParentProfileSource =
    when (val partner = ChatPartnerSource.resolve(pairing, projected)) {
        ChatPartner.Resolving -> CoParentProfileSource.Known(CoParentOnScreen.Resolving)
        ChatPartner.None -> CoParentProfileSource.Known(CoParentOnScreen.None)
        is ChatPartner.Linked -> {
            val followed = (pairing as? PairingState.Paired)?.partner
            if (followed?.id == partner.partnerUid) {
                CoParentProfileSource.Known(CoParentOnScreen.Linked(partner.partnerUid, followed))
            } else {
                CoParentProfileSource.Listen(partner.partnerUid)
            }
        }
    }
