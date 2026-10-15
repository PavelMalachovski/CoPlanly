package com.coparently.app.data.remote.firebase

import com.coparently.app.data.remote.google.GoogleAccessException
import com.coparently.app.data.remote.google.GoogleAccessFailure
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * What a failed Google token call means for the connection.
 *
 * The two callables used to fail with whatever the server threw, and the app could not tell
 * "Google ended this connection" from "the network blinked": it kept a revoked token and said
 * nothing useful. The server now names a dead token `reconnect` (`functions/index.js`,
 * `googleTokenFailure`), and these pin the reading of it — the one answer that drops a token —
 * against everything else, which must keep it.
 */
class GoogleOAuthFunctionsTest {

    private val functions = mockk<FirebaseFunctions>()
    private val refreshCallable = mockk<HttpsCallableReference>()
    private val oauth = GoogleOAuthFunctions(functions)

    @Before
    fun setUp() {
        every { functions.getHttpsCallable("refreshGoogleAccessToken") } returns refreshCallable
    }

    @Test
    fun `a refresh that worked hands the new token back`() = runTest {
        answer(Tasks.forResult(callableResult(mapOf("accessToken" to "fresh", "expiresInSeconds" to 3599))))

        val tokens = oauth.refreshAccessToken("refresh-1").getOrThrow()

        assertEquals("fresh", tokens.accessToken)
        assertEquals(3599L, tokens.expiresInSeconds)
    }

    @Test
    fun `the server's reconnect means the connection is over`() = runTest {
        answer(Tasks.forException(functionsException(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "reconnect")))

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, refreshFailure())
    }

    @Test
    fun `a server deployed before reconnect existed is read the same way`() = runTest {
        // An app newer than its backend: the fingerprint refusal still carries its old reason.
        answer(
            Tasks.forException(
                functionsException(FirebaseFunctionsException.Code.PERMISSION_DENIED, "unknown-refresh-token")
            )
        )

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, refreshFailure())
    }

    @Test
    fun `everything else keeps the connection, to be tried again`() = runTest {
        val failures = listOf(
            functionsException(FirebaseFunctionsException.Code.UNAVAILABLE, "google-unavailable"),
            functionsException(FirebaseFunctionsException.Code.DEADLINE_EXCEEDED),
            functionsException(FirebaseFunctionsException.Code.INTERNAL),
            // Google refused this deployment's own client: the owner's to fix, not the parent's.
            functionsException(FirebaseFunctionsException.Code.INTERNAL, "google-refused"),
            functionsException(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "oauth-not-configured"),
            functionsException(FirebaseFunctionsException.Code.UNAUTHENTICATED),
            IllegalStateException("not a Firebase failure at all")
        )

        failures.forEach { failure ->
            answer(Tasks.forException(failure))
            assertEquals(GoogleAccessFailure.UNAVAILABLE, refreshFailure(), "for $failure")
        }
    }

    @Test
    fun `an answer without a token keeps the connection too`() = runTest {
        answer(Tasks.forResult(callableResult(mapOf("expiresInSeconds" to 3599))))

        assertEquals(GoogleAccessFailure.UNAVAILABLE, refreshFailure())
    }

    private fun answer(task: Task<HttpsCallableResult>) {
        every { refreshCallable.call(any()) } returns task
    }

    private suspend fun refreshFailure(): GoogleAccessFailure? =
        (oauth.refreshAccessToken("refresh-1").exceptionOrNull() as? GoogleAccessException)?.failure

    private fun callableResult(data: Map<String, Any?>): HttpsCallableResult {
        val result = mockk<HttpsCallableResult>()
        every { result.getData() } returns data
        return result
    }

    private fun functionsException(
        code: FirebaseFunctionsException.Code,
        reason: String? = null
    ): FirebaseFunctionsException {
        val exception = mockk<FirebaseFunctionsException>(relaxed = true)
        every { exception.code } returns code
        every { exception.details } returns reason?.let { mapOf("reason" to it) }
        return exception
    }
}
