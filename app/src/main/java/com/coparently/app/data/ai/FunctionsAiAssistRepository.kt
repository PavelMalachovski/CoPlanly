package com.coparently.app.data.ai

import com.coparently.app.data.platform.logger
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.time.IsoText
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.YearMonth

/**
 * [AiAssistRepository] over the `aiAssist` callable, through the app's one [FirebaseFunctions]
 * instance — `FUNCTIONS_REGION` in `di/koin/FirebaseModule.kt`, `europe-west3`, where the model
 * is called too.
 *
 * **Every failure is an answer, never an exception**, as in `ExportReceipts`: a refusal is
 * classified by [AiAssistErrors], and offline, a timeout or a network error is
 * [AiAssistResult.Unavailable]. Nothing here logs a request's or an answer's text.
 */
class FunctionsAiAssistRepository(
    private val functions: FirebaseFunctions
) : AiAssistRepository {

    override suspend fun suggestReply(
        conversationId: String,
        locale: String,
        draftHint: String?
    ): AiAssistResult {
        val payload = buildMap<String, Any> {
            put(KEY_TASK, TASK_REPLY)
            put(KEY_LOCALE, locale)
            put(KEY_CONVERSATION, conversationId)
            draftHint?.trim()?.takeIf { it.isNotEmpty() }?.let { put(KEY_DRAFT_HINT, it) }
        }
        return call(payload)
    }

    override suspend fun summarizeMonth(month: YearMonth, locale: String, stats: Map<String, Any>): AiAssistResult =
        call(
            mapOf(
                KEY_TASK to TASK_MONTH_SUMMARY,
                KEY_LOCALE to locale,
                // `java.time.YearMonth.toString()`'s text, which this sent until MON-24.
                KEY_MONTH to IsoText.yearMonth(month),
                KEY_STATS to stats
            )
        )

    /** One answer from the callable, wrapped so a timeout (null) is not mistaken for an empty one. */
    private class Answer(val data: Any?)

    private suspend fun call(payload: Map<String, Any>): AiAssistResult = try {
        val answer = withTimeoutOrNull(CALL_TIMEOUT_MS) {
            Answer(functions.getHttpsCallable(CALLABLE).call(payload).await().getData())
        }
        when (answer) {
            null -> AiAssistResult.Unavailable
            else -> textOf(answer.data)?.let { AiAssistResult.Text(it) } ?: AiAssistResult.Failed
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        logger.w(TAG, "aiAssist refused: ${e.code}")
        AiAssistErrors.classify(e.code.name, e.details, e.message)
    } catch (
        // Offline, a TLS or socket failure, a backend not yet deployed: none of them is the
        // parent's to fix beyond "try again", which is what Unavailable says.
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.w(TAG, "aiAssist could not be reached", e)
        AiAssistResult.Unavailable
    }

    private fun textOf(data: Any?): String? =
        ((data as? Map<*, *>)?.get(KEY_TEXT) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "AiAssist"
        const val CALLABLE = "aiAssist"

        const val KEY_TASK = "task"
        const val KEY_LOCALE = "locale"
        const val KEY_CONVERSATION = "conversationId"
        const val KEY_DRAFT_HINT = "draftHint"
        const val KEY_MONTH = "month"
        const val KEY_STATS = "stats"
        const val KEY_TEXT = "text"

        const val TASK_REPLY = "reply"
        const val TASK_MONTH_SUMMARY = "monthSummary"

        /** A model answer takes seconds; past this the parent is told to try again. */
        const val CALL_TIMEOUT_MS = 45_000L
    }
}
