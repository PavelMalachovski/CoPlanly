package com.coparently.app.presentation.professionals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.professionals.ProfessionalAccessDuration
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.presentation.pairing.pairingMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The invite sheet's state (MON-18).
 *
 * Role and length are chosen **before** the code exists, for the reason `FriendInviteState` gives:
 * once a code has been read out, the invitation document already carries both.
 *
 * @property isOpen False when the sheet is closed.
 * @property role Who is being invited.
 * @property duration How long the access will last, at most.
 * @property invite The minted invitation, or null before [ProfessionalsViewModel.createInvite].
 * @property isBusy A call is in flight.
 * @property errorRes Why the last attempt failed, as a resource the composable resolves.
 */
data class ProfessionalInviteState(
    val isOpen: Boolean = false,
    val role: ProfessionalRole = ProfessionalRole.MEDIATOR,
    val duration: ProfessionalAccessDuration = ProfessionalAccessDuration.DEFAULT,
    val invite: GuestInvite? = null,
    val isBusy: Boolean = false,
    @StringRes val errorRes: Int? = null
)

/**
 * The professional's own "I have a code" state.
 *
 * @property code What they have typed, upper-cased as they go.
 * @property isBusy A call is in flight.
 * @property accepted The code was redeemed; access opens once both parents consent.
 * @property errorRes Why the last attempt failed.
 */
data class ProfessionalRedeemState(
    val code: String = "",
    val isBusy: Boolean = false,
    val accepted: Boolean = false,
    @StringRes val errorRes: Int? = null
)

/**
 * Professional access, from both ends (MON-18): the parents' list with its consent and revoke
 * actions and the invite sheet, and — on a professional's phone — the families they may read and
 * the field to redeem a code.
 *
 * One ViewModel for both sides for the reason `FriendViewModel` is one: the two lists are disjoint
 * (a parent's grants name them in `familyParents`, a professional's name them in `proUid`), and a
 * person who is both sees both.
 */
@HiltViewModel
class ProfessionalsViewModel @Inject constructor(
    private val repository: ProfessionalRepository
) : ViewModel() {

    /** Grants over this parent's families — active, waiting or expired. */
    val familyGrants: StateFlow<List<ProfessionalGrant>> = repository.observeFamilyGrants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Grants this account holds as a professional. */
    val myGrants: StateFlow<List<ProfessionalGrant>> = repository.observeMyGrants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _invite = MutableStateFlow(ProfessionalInviteState())

    /** The invite sheet. */
    val invite: StateFlow<ProfessionalInviteState> = _invite.asStateFlow()

    private val _redeem = MutableStateFlow(ProfessionalRedeemState())

    /** The professional's code field. */
    val redeem: StateFlow<ProfessionalRedeemState> = _redeem.asStateFlow()

    private val _openGrantId = MutableStateFlow<String?>(null)

    /** The grant whose card is open, by id, or null. */
    val openGrantId: StateFlow<String?> = _openGrantId.asStateFlow()

    private val _actionError = MutableStateFlow<Int?>(null)

    /** Why the last consent or revoke failed, or null. */
    val actionError: StateFlow<Int?> = _actionError.asStateFlow()

    /** The signed-in uid, which decides whose consent a row is waiting for. */
    fun viewerUid(): String? = repository.currentUid()

    /** Opens the invite sheet on the defaults. */
    fun openInvite() {
        _invite.value = ProfessionalInviteState(isOpen = true)
    }

    /** Picks a role. Ignored once a code exists — by then it is stamped. */
    fun chooseRole(role: ProfessionalRole) {
        _invite.update { if (it.invite != null) it else it.copy(role = role) }
    }

    /** Picks a length. Ignored once a code exists. */
    fun chooseDuration(duration: ProfessionalAccessDuration) {
        _invite.update { if (it.invite != null) it else it.copy(duration = duration) }
    }

    /** Mints the invitation — this parent's consent, made at the moment they hand out the code. */
    fun createInvite() {
        val state = _invite.value
        if (state.isBusy || state.invite != null) return
        _invite.value = state.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            repository.invite(state.role, state.duration.expiryFrom(System.currentTimeMillis())).fold(
                onSuccess = { created -> _invite.update { it.copy(invite = created, isBusy = false) } },
                onFailure = { e -> _invite.update { it.copy(isBusy = false, errorRes = e.pairingMessageRes()) } }
            )
        }
    }

    /** Closes the sheet; the invitation itself stands until it is redeemed or runs out. */
    fun dismissInvite() {
        _invite.value = ProfessionalInviteState()
    }

    /** Types into the code field. */
    fun updateCode(code: String) {
        _redeem.update { it.copy(code = code.uppercase(), errorRes = null, accepted = false) }
    }

    /** Redeems the typed code through the professional callable only. */
    fun redeemCode() {
        val state = _redeem.value
        if (state.isBusy || state.code.isBlank()) return
        _redeem.value = state.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            repository.acceptInvite(state.code).fold(
                onSuccess = { _redeem.value = ProfessionalRedeemState(accepted = true) },
                onFailure = { e -> _redeem.update { it.copy(isBusy = false, errorRes = e.pairingMessageRes()) } }
            )
        }
    }

    /** Opens [grantId]'s card. */
    fun openGrant(grantId: String) {
        _openGrantId.value = grantId
    }

    /** Closes the card. */
    fun closeGrant() {
        _openGrantId.value = null
    }

    /** Adds this parent's consent to [grantId]; the card closes only when it landed. */
    fun consent(grantId: String) = act { repository.consent(grantId) }

    /** Ends [grantId]. One parent is enough. */
    fun revoke(grantId: String) = act { repository.revoke(grantId) }

    /** Clears [actionError] once the UI has shown it. */
    fun clearActionError() {
        _actionError.value = null
    }

    private fun act(call: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            call().fold(
                onSuccess = { _openGrantId.value = null },
                onFailure = { _actionError.value = R.string.professional_action_failed }
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
