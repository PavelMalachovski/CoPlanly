package com.coparently.app.data.ai

import com.coparently.app.domain.ai.AiAssistResult
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The `aiAssist` callable's refusals: the reason decides wherever the server put it, because
 * reasons share codes ("switched off" and "no consent" are both FAILED_PRECONDITION), and the code
 * decides only without a reason.
 */
class AiAssistErrorsTest {

    @Test
    fun `every reason the server names has its own answer`() {
        val expected = mapOf(
            "ai-disabled" to AiAssistResult.Disabled,
            "ai-consent-required" to AiAssistResult.ConsentRequired,
            "ai-rate-limited" to AiAssistResult.RateLimited,
            "ai-unavailable" to AiAssistResult.Unavailable,
            "ai-invalid-request" to AiAssistResult.InvalidRequest,
            "ai-empty-thread" to AiAssistResult.EmptyThread,
            "ai-not-participant" to AiAssistResult.NotParticipant,
            "ai-pairing-not-live" to AiAssistResult.PairingNotLive
        )
        expected.forEach { (reason, result) ->
            assertEquals(result, AiAssistErrors.classify("INTERNAL", mapOf("reason" to reason), "x"), reason)
        }
    }

    @Test
    fun `a reason in details decides over the code`() {
        assertEquals(
            AiAssistResult.ConsentRequired,
            AiAssistErrors.classify("FAILED_PRECONDITION", mapOf("reason" to "ai-consent-required"), "whatever")
        )
        assertEquals(
            AiAssistResult.Disabled,
            AiAssistErrors.classify("FAILED_PRECONDITION", mapOf("reason" to "ai-disabled"), null)
        )
        assertEquals(
            AiAssistResult.EmptyThread,
            AiAssistErrors.classify("INVALID_ARGUMENT", mapOf("reason" to "ai-empty-thread"), null)
        )
        assertEquals(
            AiAssistResult.PairingNotLive,
            AiAssistErrors.classify("PERMISSION_DENIED", mapOf("reason" to "ai-pairing-not-live"), null)
        )
    }

    @Test
    fun `a reason as the bare details or in the message is read too`() {
        assertEquals(
            AiAssistResult.RateLimited,
            AiAssistErrors.classify("RESOURCE_EXHAUSTED", "ai-rate-limited", null)
        )
        assertEquals(
            AiAssistResult.ConsentRequired,
            AiAssistErrors.classify("FAILED_PRECONDITION", null, "ai-consent-required")
        )
        assertEquals(
            AiAssistResult.NotParticipant,
            AiAssistErrors.classify("PERMISSION_DENIED", null, "Refused (ai-not-participant)")
        )
    }

    @Test
    fun `without a reason the code decides`() {
        assertEquals(AiAssistResult.RateLimited, AiAssistErrors.classify("RESOURCE_EXHAUSTED", null, null))
        assertEquals(AiAssistResult.Unavailable, AiAssistErrors.classify("UNAVAILABLE", null, null))
        assertEquals(AiAssistResult.Unavailable, AiAssistErrors.classify("DEADLINE_EXCEEDED", null, null))
        assertEquals(AiAssistResult.InvalidRequest, AiAssistErrors.classify("INVALID_ARGUMENT", null, null))
        assertEquals(AiAssistResult.Disabled, AiAssistErrors.classify("NOT_FOUND", null, "NOT_FOUND"))
        // A bare FAILED_PRECONDITION says neither which precondition nor that consent would help.
        assertEquals(AiAssistResult.Failed, AiAssistErrors.classify("FAILED_PRECONDITION", null, null))
        assertEquals(AiAssistResult.Failed, AiAssistErrors.classify("PERMISSION_DENIED", null, null))
        assertEquals(AiAssistResult.Failed, AiAssistErrors.classify("INTERNAL", mapOf("reason" to "other"), "x"))
        assertEquals(AiAssistResult.Failed, AiAssistErrors.classify(null, null, null))
    }
}
