package com.coparently.app.data.remote.firebase

/**
 * A way to sign in to the app's own account (MON-24 phase 4, part A): what Settings' "Sign-in
 * methods" lists, links and unlinks through [AccountSignIn].
 *
 * @property providerId Firebase Auth's provider id for the method, as a user's `providerData`
 *   names it.
 */
enum class SignInMethod(val providerId: String) {
    /** An e-mail address and a password. */
    EMAIL("password"),

    /** A Google account. */
    GOOGLE("google.com"),

    /** An Apple ID. */
    APPLE("apple.com");

    companion object {
        /** The method Firebase names [providerId], or null for any other provider (`firebase`, a phone). */
        fun fromProviderId(providerId: String): SignInMethod? = entries.firstOrNull { it.providerId == providerId }
    }
}

/**
 * What a sign-in with a provider's credential reported beside the signed-in user.
 *
 * @property isNewUser True when Firebase created the account at this sign-in
 *   (`AdditionalUserInfo.isNewUser`): the one case the onboarding asks "Already use CoPlanly?".
 */
data class SignInOutcome(val isNewUser: Boolean)

/** Why linking or unlinking a sign-in method failed, as the screen words it. */
sealed interface LinkFailure {
    /**
     * The credential, or the address, already belongs to another account. Nothing was linked, the
     * signed-in account is unchanged, and nothing about the other account is named.
     */
    data object InUseByAnotherAccount : LinkFailure

    /** The method is the account's last one, which is never removed. */
    data object LastMethod : LinkFailure

    /** Firebase wants a recent sign-in before it changes the account's methods. */
    data object NeedsRecentSignIn : LinkFailure

    /** Anything else: the network, the server, a method that was not linked. */
    data object Other : LinkFailure
}

/**
 * A failed link or unlink, carrying its [failure] and the platform's own exception as the cause.
 *
 * @property failure Which [LinkFailure] it was.
 */
class LinkException(val failure: LinkFailure, cause: Throwable? = null) :
    Exception("Sign-in method change failed: $failure", cause)

/**
 * Why [method] may not be unlinked from an account whose methods are [linked] (null while nobody is
 * signed in), or null when it may: a method the account does not hold is [LinkFailure.Other], and
 * the account's last method is [LinkFailure.LastMethod], which is never removed.
 */
fun unlinkRefusal(linked: Set<SignInMethod>?, method: SignInMethod): LinkFailure? = when {
    linked == null || method !in linked -> LinkFailure.Other
    linked.size <= 1 -> LinkFailure.LastMethod
    else -> null
}
