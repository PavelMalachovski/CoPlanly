package com.coparently.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.presentation.common.UiText
import com.coparently.app.resources.Res
import com.coparently.app.resources.new_account_ask_retry_failed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Onboarding's "Already use CoPlanly?" (board 3): shown while [ask] is true, before the first step.
 *
 * "This is a new account" ([keepNewAccount]) records the answer. "Sign out and try again"
 * ([signOutAndRetry]) deletes the empty account through [AccountDeletion] first, because an empty
 * account left behind would hold the method and block its later linking (spec §3.8 call 4), and only
 * then ends the session through [AuthSession]. A deletion that fails signs nothing out: the question
 * stays and [errors] says why, so the parent can try again.
 */
class NewAccountAskViewModel(
    private val newAccountAsk: NewAccountAsk,
    signedInUser: SignedInUser,
    private val accountDeletion: AccountDeletion,
    private val authSession: AuthSession
) : ViewModel() {

    private val uid: String? = signedInUser.getCurrentUid()

    private val _ask = MutableStateFlow(uid?.let(newAccountAsk::isPending) == true)

    /** Whether the question is drawn: the signed-in account was just created and has not answered. */
    val ask: StateFlow<Boolean> = _ask.asStateFlow()

    private val _working = MutableStateFlow(false)

    /** True while "Sign out and try again" is deleting the account and signing out. */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _signedOut = MutableStateFlow(false)

    /** True once the new account is deleted and the session ended: the screen leaves for sign-in. */
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    private val _errors = MutableSharedFlow<UiText>(extraBufferCapacity = 1)

    /** Why "Sign out and try again" failed, once per failure, for a snackbar. */
    val errors: SharedFlow<UiText> = _errors.asSharedFlow()

    /** "This is a new account": the question is answered and onboarding goes on. */
    fun keepNewAccount() {
        uid?.let(newAccountAsk::answer)
        _ask.value = false
    }

    /**
     * "Sign out and try again": deletes the new account, then signs out. A failed deletion leaves
     * the account, the session and the question as they were, and says so.
     */
    fun signOutAndRetry() {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                accountDeletion.deleteAccount().fold(
                    onSuccess = {
                        // The account is gone; a sign-out that fails now leaves only a session
                        // with no account behind it, which the next start finds signed out.
                        authSession.signOutCompletely().onFailure { error ->
                            logger.w(TAG, "Signing out after the deletion failed: ${error::class.simpleName}")
                        }
                        // The question stays up (ask is left as it is) until the screen has left
                        // for sign-in, so the wizard never flashes between the two.
                        uid?.let(newAccountAsk::answer)
                        _signedOut.value = true
                    },
                    onFailure = { error ->
                        logger.w(TAG, "Deleting the new account failed: ${error::class.simpleName}")
                        _errors.tryEmit(UiText.Res(Res.string.new_account_ask_retry_failed))
                    }
                )
            } finally {
                _working.value = false
            }
        }
    }

    private companion object {
        const val TAG = "NewAccountAsk"
    }
}
