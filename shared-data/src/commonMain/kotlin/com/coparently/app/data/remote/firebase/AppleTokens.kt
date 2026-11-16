package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.CancellationException

/**
 * Apple's refresh token, kept and revoked by the server (MON-24 phase 4, part A).
 *
 * The phone never holds the token: it hands Apple's authorization code to `exchangeAppleAuthCode`,
 * which redeems it with the client secret only the functions hold and keeps the refresh token in the
 * closed `apple_tokens/{uid}`; `unlinkApple` revokes it, deletes that document and unlinks Apple from
 * the Firebase account, refusing the account's last method (`last-method`).
 *
 * @property functions The callables, in `europe-west3`.
 */
class AppleTokens(private val functions: CallableFunctions) {

    /**
     * Has the server exchange Apple's authorization [code] and keep the refresh token. Apple must
     * already be linked to the signed-in account; the callable refuses a code for another Apple ID.
     * Every failure is a [LinkException] with [LinkFailure.Other], the callable's refusal its cause.
     *
     * @param clientId The client the code was issued to: the bundle id, or the Services ID.
     */
    suspend fun store(code: String, clientId: String): Result<Unit> =
        call(EXCHANGE, mapOf("code" to code, "clientId" to clientId))

    /**
     * Has the server revoke Apple's token, delete it and unlink Apple from the account. A refusal
     * of the account's last method fails with [LinkFailure.LastMethod], anything else with
     * [LinkFailure.Other].
     */
    suspend fun unlink(): Result<Unit> = call(UNLINK, emptyMap())

    private suspend fun call(name: String, payload: Map<String, Any>): Result<Unit> = try {
        functions.call(name, payload)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(LinkException(failureOf(e), e))
    }

    companion object {
        /** The callable that exchanges Apple's code. */
        const val EXCHANGE = "exchangeAppleAuthCode"

        /** The callable that revokes Apple's token and unlinks Apple. */
        const val UNLINK = "unlinkApple"

        /** The [LinkFailure] a callable's refusal [e] stands for. */
        fun failureOf(e: Throwable): LinkFailure {
            val reason = ((e as? CallableException)?.details as? Map<*, *>)?.get("reason") as? String
            return if (reason == "last-method") LinkFailure.LastMethod else LinkFailure.Other
        }
    }
}
