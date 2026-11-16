package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [AppleTokens] calls the two callables with what the server reads, and words their refusals. */
class AppleTokensTest {

    private class RecordingFunctions(private val failure: Exception? = null) : CallableFunctions {
        val calls = mutableListOf<Pair<String, Map<String, Any>>>()

        override suspend fun call(name: String, payload: Map<String, Any>): Any? {
            calls += name to payload
            failure?.let { throw it }
            return mapOf("stored" to true)
        }
    }

    private fun refusal(reason: String) =
        CallableException(CallableCode.FAILED_PRECONDITION, mapOf("reason" to reason), reason, null)

    @Test
    fun `store sends the code and the client id`() = runTest {
        val functions = RecordingFunctions()
        val result = AppleTokens(functions).store("the-code", "app.coplanly")
        assertTrue(result.isSuccess)
        assertEquals(
            listOf("exchangeAppleAuthCode" to mapOf<String, Any>("code" to "the-code", "clientId" to "app.coplanly")),
            functions.calls
        )
    }

    @Test
    fun `unlink calls unlinkApple with nothing`() = runTest {
        val functions = RecordingFunctions()
        assertTrue(AppleTokens(functions).unlink().isSuccess)
        assertEquals(listOf("unlinkApple" to emptyMap<String, Any>()), functions.calls)
    }

    @Test
    fun `the last method refused by the server is LastMethod`() = runTest {
        val failure = AppleTokens(RecordingFunctions(refusal("last-method"))).unlink().exceptionOrNull()
        assertEquals(LinkFailure.LastMethod, (failure as LinkException).failure)
    }

    @Test
    fun `any other refusal is Other`() = runTest {
        val failure = AppleTokens(RecordingFunctions(refusal("apple-account-mismatch")))
            .store("c", "app.coplanly").exceptionOrNull()
        assertEquals(LinkFailure.Other, (failure as LinkException).failure)
    }
}
