package com.coparently.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.AccountLinking
import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInMethod
import com.coparently.app.data.remote.firebase.unlinkRefusal
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.auth.messageRes
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.resources.Res
import com.coparently.app.resources.sign_in_methods_failed
import com.coparently.app.resources.sign_in_methods_in_use
import com.coparently.app.resources.sign_in_methods_last_note
import com.coparently.app.resources.sign_in_methods_sign_in_again
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * What Settings' "Sign-in methods" shows (MON-24 phase 4, part A; board 2).
 *
 * @property linked The account's linked methods, or null until they are first read
 * @property working The method being added or removed, or null; every row's action waits for it
 * @property banner Why the last add or remove failed, shown in an attention banner until the next
 *   one; null when there is nothing to say
 */
data class SignInMethodsUiState(
    val linked: Set<SignInMethod>? = null,
    val working: SignInMethod? = null,
    val banner: UiText? = null
) {
    /** The linked methods in the order the screen lists them, for Settings' row value. */
    val linkedInOrder: List<SignInMethod>
        get() = SignInMethod.entries.filter { it in linked.orEmpty() }

    /**
     * The screen's rows: each method that is linked or that this phone can add ([offered]), in
     * [SignInMethod]'s order. A method neither linked nor offered has no row: a row whose Add could
     * not work would promise what the phone cannot keep (design item 8). Nothing while the methods
     * have not been read yet.
     *
     * A linked method always has its row, offered or not: an Apple ID already linked is listed with
     * Remove (and named in Settings' value) even while the build's `APPLE_SIGN_IN` flag is off, so
     * it can still be removed. The flag gates only adding Apple (controller ruling, Task 6 review).
     */
    fun rows(offered: Set<SignInMethod>): List<SignInMethodRow> {
        val methods = linked ?: return emptyList()
        return SignInMethod.entries
            .filter { it in methods || it in offered }
            .map { method ->
                SignInMethodRow(
                    method = method,
                    linked = method in methods,
                    canRemove = unlinkRefusal(methods, method) == null
                )
            }
    }

    companion object {
        /**
         * The methods this phone can add: an e-mail and a password always, Google where the
         * credential sheet is [googleAvailable], Apple where [appleAvailable] (the build's
         * `APPLE_SIGN_IN` flag on a platform with Apple's sheet).
         */
        fun offeredMethods(googleAvailable: Boolean, appleAvailable: Boolean): Set<SignInMethod> =
            buildSet {
                add(SignInMethod.EMAIL)
                if (googleAvailable) add(SignInMethod.GOOGLE)
                if (appleAvailable) add(SignInMethod.APPLE)
            }
    }
}

/**
 * One row of "Sign-in methods".
 *
 * @property method The method
 * @property linked Whether the account holds it: the row offers Remove, else Add
 * @property canRemove Whether Remove is drawn: never for the account's last method
 */
data class SignInMethodRow(
    val method: SignInMethod,
    val linked: Boolean,
    val canRemove: Boolean
)

/**
 * Settings → Account → "Sign-in methods" (MON-24 phase 4, part A): lists e-mail, Google and Apple
 * as linked or not, adds one to the signed-in account and removes one, never the last.
 *
 * Every rule that matters is the seam's ([AccountLinking]): a method another account holds links
 * nothing and the signed-in account stays as it is, and the last method is refused. This only words
 * what happened, in one [SignInMethodsUiState.banner] that never names the other account, and checks
 * the last method itself before asking, so [remove] of it makes no call at all.
 *
 * Google's and Apple's sheets are the shell's, so the screen hands [CredentialSignIn] to each call
 * and the ViewModel never keeps it, as `AuthViewModel` does. An Apple ID is linked by the sheet
 * itself (`CredentialSignIn.linkApple`), around [AccountLinking], so the methods are read again
 * through [AccountLinking.refresh] afterwards.
 *
 * @property accountLinking The signed-in account's methods
 * @property authErrors Which Google sheet failures are a cancel, and how the others are worded
 */
class SignInMethodsViewModel(
    private val accountLinking: AccountLinking,
    private val authErrors: AuthErrors
) : ViewModel() {

    private val working = MutableStateFlow<SignInMethod?>(null)
    private val banner = MutableStateFlow<UiText?>(null)

    /**
     * The screen's state. Started eagerly, because [remove] reads the linked methods from it to
     * refuse the last one, and must not read an initial value no screen has collected past.
     */
    val state: StateFlow<SignInMethodsUiState> =
        combine(accountLinking.linkedMethods(), working, banner) { linked, busy, message ->
            SignInMethodsUiState(linked = linked, working = busy, banner = message)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SignInMethodsUiState())

    /**
     * Reads the methods again ([AccountLinking.refresh]), as the screen does each time it opens: a
     * sheet's link that finished after the screen left (a rotation, Back) is then listed.
     */
    fun refresh() {
        viewModelScope.launch { accountLinking.refresh() }
    }

    /** Whether [method] may be removed now: it is linked and it is not the account's last. */
    fun canRemove(method: SignInMethod): Boolean = unlinkRefusal(state.value.linked, method) == null

    /** Adds an e-mail address and a password to the account. Blank fields add nothing. */
    fun addEmail(email: String, password: String) {
        if (email.isBlank() || password.isEmpty() || working.value != null) return
        viewModelScope.launch {
            change(SignInMethod.EMAIL) { accountLinking.linkEmail(email.trim(), password) }
        }
    }

    /**
     * Adds the Google account the parent picks in [credentials]' sheet. A sheet the parent closed
     * adds nothing and says nothing; another failure of the sheet is worded as the sign-in screen
     * words it.
     */
    suspend fun addGoogle(credentials: CredentialSignIn) {
        if (working.value != null) return
        change(SignInMethod.GOOGLE) {
            credentials.googleIdToken().fold(
                onSuccess = { idToken -> accountLinking.linkGoogle(idToken) },
                onFailure = { e -> Result.failure(SheetFailure(authErrors.fromGoogle(e)?.messageRes(), e)) }
            )
        }
    }

    /**
     * Adds an Apple ID through [credentials]' sheet, which links it itself; the methods are then
     * read again ([AccountLinking.refresh]), since the link went around [accountLinking]. A sheet
     * the parent closed adds nothing and says nothing.
     */
    suspend fun addApple(credentials: CredentialSignIn) {
        if (working.value != null) return
        change(SignInMethod.APPLE) {
            credentials.linkApple().onSuccess { accountLinking.refresh() }
        }
    }

    /**
     * Removes [method] once the parent confirmed. The account's last method is never removed: it is
     * refused here, before any call, and by [AccountLinking.unlink] again.
     */
    fun remove(method: SignInMethod) {
        if (!canRemove(method) || working.value != null) return
        viewModelScope.launch {
            change(method) { accountLinking.unlink(method) }
        }
    }

    /** Runs [call] for [method], clearing the banner first and wording its failure after. */
    private suspend fun change(method: SignInMethod, call: suspend () -> Result<Unit>) {
        working.value = method
        banner.value = null
        try {
            call().onFailure { e ->
                val message = messageOf(e)
                // A sheet the parent closed is not a failure: nothing to say, nothing to log.
                if (message != null) {
                    logger.w(TAG, "Changing the $method sign-in method failed: ${e::class.simpleName}")
                }
                banner.value = message
            }
        } finally {
            working.value = null
        }
    }

    /** The banner for a failed add or remove, or null for one the parent ended by closing a sheet. */
    private fun messageOf(e: Throwable): UiText? = when {
        e is AppleSignInCancelledException -> null
        e is SheetFailure -> e.reason?.let { UiText.Res(it) }
        e is LinkException -> UiText.Res(
            when (e.failure) {
                LinkFailure.InUseByAnotherAccount -> Res.string.sign_in_methods_in_use
                LinkFailure.NeedsRecentSignIn -> Res.string.sign_in_methods_sign_in_again
                LinkFailure.LastMethod -> Res.string.sign_in_methods_last_note
                LinkFailure.Other -> Res.string.sign_in_methods_failed
            }
        )
        else -> UiText.Res(Res.string.sign_in_methods_failed)
    }

    /**
     * Google's sheet ended without a token: [reason] is how the sign-in screen words it, or null
     * when the parent closed the sheet.
     */
    private class SheetFailure(
        val reason: StringResource?,
        cause: Throwable
    ) : Exception(cause)

    private companion object {
        const val TAG = "SignInMethods"
    }
}
