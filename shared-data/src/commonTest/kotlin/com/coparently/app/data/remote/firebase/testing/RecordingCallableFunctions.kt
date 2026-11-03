package com.coparently.app.data.remote.firebase

/**
 * A [CallableFunctions] for tests: records each call and answers with [answer], which may throw —
 * a [CallableException] for a refusal, as the SDK halves hand one on.
 */
class RecordingCallableFunctions(
    var answer: suspend (name: String, payload: Map<String, Any>) -> Any? = { _, _ -> null }
) : CallableFunctions {

    /** Every call, in order: the function's name and the payload. */
    val calls = mutableListOf<Pair<String, Map<String, Any>>>()

    override suspend fun call(name: String, payload: Map<String, Any>): Any? {
        calls += name to payload
        return answer(name, payload)
    }

    companion object {
        /** A refusal with [code] and, when given, a `details` map naming [reason]. */
        fun refusal(code: CallableCode, reason: String? = null, cause: String? = null): CallableException =
            CallableException(
                code = code,
                details = reason?.let { why ->
                    buildMap {
                        put("reason", why)
                        cause?.let { put("cause", it) }
                    }
                },
                message = "refused: ${code.name}",
                cause = null
            )
    }
}
