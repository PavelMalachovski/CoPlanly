package com.coparently.app.presentation.pairing

import androidx.annotation.StringRes
import com.coparently.app.R
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.model.PairingError

/**
 * The string resource that explains a failed invitation to the user.
 *
 * Shared by every screen that redeems or mints one — pairing, guest access and the calendar
 * friend (item 16) — because the three flows fail in the same ways and a second copy of this
 * `when` would be a second place for a new [PairingError] to go unhandled. It returns the id
 * rather than the resolved string so a ViewModel can hold it without a `Context`, exactly as
 * `AuthError.messageRes()` does for the login screen.
 *
 * @return the id of the localized message for whatever went wrong.
 */
@StringRes
fun Throwable.pairingMessageRes(): Int {
    val error = (this as? PairingException)?.error
    return when (error) {
        PairingError.NotFound -> R.string.pairing_error_not_found
        PairingError.Expired -> R.string.pairing_error_expired
        PairingError.NotPending -> R.string.pairing_error_not_pending
        PairingError.SelfPairing -> R.string.pairing_error_self_pairing
        PairingError.AlreadyPaired -> R.string.pairing_error_already_paired
        PairingError.WrongRecipient -> R.string.pairing_error_wrong_recipient
        PairingError.Network -> R.string.pairing_error_network
        else -> kindMessageRes(error)
    }
}

/**
 * The half of [pairingMessageRes] about *which kind* of code was offered where, and about the
 * grant it would have opened — split off so neither `when` grows past what one reader can hold.
 */
@StringRes
private fun kindMessageRes(error: PairingError?): Int = when (error) {
    PairingError.FriendInvitation -> R.string.pairing_error_friend_invitation
    PairingError.NotFriendInvitation -> R.string.pairing_error_not_friend_invitation
    PairingError.ProfessionalInvitation -> R.string.professional_error_is_professional_invitation
    PairingError.NotProfessionalInvitation -> R.string.professional_error_not_professional_invitation
    PairingError.InviterNotPaired -> R.string.professional_error_inviter_not_paired
    PairingError.GrantEnded -> R.string.access_error_grant_ended
    PairingError.AlreadyEntitled -> R.string.access_error_already_in_family
    else -> R.string.pairing_error_unknown
}
