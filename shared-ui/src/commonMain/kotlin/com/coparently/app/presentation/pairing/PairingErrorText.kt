package com.coparently.app.presentation.pairing

import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.model.PairingError
import com.coparently.app.resources.Res
import com.coparently.app.resources.access_error_already_in_family
import com.coparently.app.resources.access_error_grant_ended
import com.coparently.app.resources.pairing_error_already_paired
import com.coparently.app.resources.pairing_error_enter_their_code
import com.coparently.app.resources.pairing_error_expired
import com.coparently.app.resources.pairing_error_friend_invitation
import com.coparently.app.resources.pairing_error_network
import com.coparently.app.resources.pairing_error_not_found
import com.coparently.app.resources.pairing_error_not_friend_invitation
import com.coparently.app.resources.pairing_error_not_pending
import com.coparently.app.resources.pairing_error_self_pairing
import com.coparently.app.resources.pairing_error_slot_conflict
import com.coparently.app.resources.pairing_error_unknown
import com.coparently.app.resources.pairing_error_wrong_recipient
import com.coparently.app.resources.professional_error_inviter_not_paired
import com.coparently.app.resources.professional_error_is_professional_invitation
import com.coparently.app.resources.professional_error_not_professional_invitation
import org.jetbrains.compose.resources.StringResource

/**
 * The string resource that explains a failed invitation to the user.
 *
 * Shared by every screen that redeems or mints one through these callables — pairing, the
 * calendar friend (item 16) and professionals (item 29) — because the three flows fail in the same
 * ways and a second copy of this `when` would be a second place for a new [PairingError] to go
 * unhandled. (A child's guest access words its own failures.) It returns the resource rather than
 * the resolved string so a ViewModel can hold it without a `Context` (CQ-14). In `:shared-ui` since
 * MON-24 phase 2 (Task 10), a Compose Multiplatform [StringResource] the screen reads with that
 * library's `stringResource`.
 *
 * @return the localized message for whatever went wrong.
 */
fun Throwable.pairingMessageRes(): StringResource {
    val error = (this as? PairingException)?.error
    return when (error) {
        PairingError.NotFound -> Res.string.pairing_error_not_found
        PairingError.Expired -> Res.string.pairing_error_expired
        PairingError.NotPending -> Res.string.pairing_error_not_pending
        PairingError.SelfPairing -> Res.string.pairing_error_self_pairing
        PairingError.AlreadyPaired -> Res.string.pairing_error_already_paired
        PairingError.WrongRecipient -> Res.string.pairing_error_wrong_recipient
        PairingError.EnterTheirCode -> Res.string.pairing_error_enter_their_code
        PairingError.SlotConflict -> Res.string.pairing_error_slot_conflict
        PairingError.Network -> Res.string.pairing_error_network
        else -> kindMessageRes(error)
    }
}

/**
 * The half of [pairingMessageRes] about *which kind* of code was offered where, and about the
 * grant it would have opened — split off so neither `when` grows past what one reader can hold.
 */
private fun kindMessageRes(error: PairingError?): StringResource = when (error) {
    PairingError.FriendInvitation -> Res.string.pairing_error_friend_invitation
    PairingError.NotFriendInvitation -> Res.string.pairing_error_not_friend_invitation
    PairingError.ProfessionalInvitation -> Res.string.professional_error_is_professional_invitation
    PairingError.NotProfessionalInvitation -> Res.string.professional_error_not_professional_invitation
    PairingError.InviterNotPaired -> Res.string.professional_error_inviter_not_paired
    PairingError.GrantEnded -> Res.string.access_error_grant_ended
    PairingError.AlreadyEntitled -> Res.string.access_error_already_in_family
    else -> Res.string.pairing_error_unknown
}
