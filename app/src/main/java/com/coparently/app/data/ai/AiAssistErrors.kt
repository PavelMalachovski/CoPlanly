package com.coparently.app.data.ai

import com.coparently.app.domain.ai.AiAssistResult

/**
 * The `aiAssist` callable's refusals, turned into an [AiAssistResult] so nothing above the data
 * layer inspects a Firebase error code or its text — the `PairingFunctions` rule.
 *
 * Kept free of Firebase types so the JVM tests reach every branch: the caller passes the
 * exception's code **name** (`FirebaseFunctionsException.Code.name`), its `details` and its message.
 */
internal object AiAssistErrors {

    /** The server's reasons, as the callable's contract names them, and what each one means. */
    private val REASONS: Map<String, AiAssistResult> = mapOf(
        "ai-disabled" to AiAssistResult.Disabled,
        "ai-consent-required" to AiAssistResult.ConsentRequired,
        "ai-rate-limited" to AiAssistResult.RateLimited,
        "ai-unavailable" to AiAssistResult.Unavailable,
        "ai-invalid-request" to AiAssistResult.InvalidRequest,
        "ai-empty-thread" to AiAssistResult.EmptyThread,
        "ai-not-participant" to AiAssistResult.NotParticipant,
        "ai-pairing-not-live" to AiAssistResult.PairingNotLive
    )

    /**
     * What a refusal means.
     *
     * The reason decides first, wherever the server put it — `details.reason`, `details` as a bare
     * string, or the message — because reasons share codes (`FAILED_PRECONDITION` is both
     * "switched off" and "no consent"), and asking for consent again when the assist is off would
     * be a dialog that leads nowhere. Without a known reason the code decides.
     *
     * @param code `FirebaseFunctionsException.Code.name`, or null for an exception that is not one
     * @param details The exception's `details`
     * @param message The exception's message
     */
    fun classify(code: String?, details: Any?, message: String?): AiAssistResult =
        reasonIn(details, message)?.let { REASONS.getValue(it) } ?: byCode(code)

    private fun reasonIn(details: Any?, message: String?): String? {
        val candidates = listOfNotNull(
            (details as? Map<*, *>)?.get("reason") as? String,
            details as? String,
            message
        )
        // An exact reason first; then one quoted inside a longer message. The longest match wins
        // inside a message, so no reason can be mistaken for another that it happens to contain.
        return candidates.firstNotNullOfOrNull { text -> text.trim().takeIf { it in REASONS } }
            ?: candidates.firstNotNullOfOrNull { text ->
                REASONS.keys.filter { text.contains(it) }.maxByOrNull { it.length }
            }
    }

    private fun byCode(code: String?): AiAssistResult = when (code) {
        "RESOURCE_EXHAUSTED" -> AiAssistResult.RateLimited
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> AiAssistResult.Unavailable
        "INVALID_ARGUMENT" -> AiAssistResult.InvalidRequest
        // A callable that is not deployed (yet) answers NOT_FOUND; the assist is simply not there.
        "NOT_FOUND", "UNIMPLEMENTED" -> AiAssistResult.Disabled
        else -> AiAssistResult.Failed
    }
}
