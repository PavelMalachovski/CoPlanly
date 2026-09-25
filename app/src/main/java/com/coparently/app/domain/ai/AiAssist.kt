package com.coparently.app.domain.ai

import java.time.YearMonth

/**
 * Whether this build offers the AI assist at all — `BuildConfig.AI_ASSIST_ENABLED`, on in debug and
 * off in release until billing (MON-11) exists.
 *
 * A value rather than a static read so a ViewModel test can hold either answer. While [enabled] is
 * false **no AI affordance renders**: not a disabled button, not a teaser (design item 8).
 *
 * @property enabled Whether reply suggestions, the month summary and the consent row are offered
 */
data class AiAssistAvailability(val enabled: Boolean)

/**
 * How an AI request ended. Every failure is an answer, never an exception: the screens word each
 * one, and nothing but [Text] ever reaches the parent's composer or screen as content.
 */
sealed interface AiAssistResult {

    /** The draft or summary, trimmed and never blank. */
    data class Text(val text: String) : AiAssistResult

    /** The server has the assist switched off (`ai-disabled`), or the callable is not deployed. */
    data object Disabled : AiAssistResult

    /** The server holds no current consent for this parent (`ai-consent-required`): ask again. */
    data object ConsentRequired : AiAssistResult

    /** Too many requests in a short time (`ai-rate-limited`). */
    data object RateLimited : AiAssistResult

    /** The model or the network could not be reached (`ai-unavailable`, offline, timed out). */
    data object Unavailable : AiAssistResult

    /** The server refused the request's shape (`ai-invalid-request`) — a client out of step with it. */
    data object InvalidRequest : AiAssistResult

    /** The thread holds no messages to reply to (`ai-empty-thread`); nothing was counted. */
    data object EmptyThread : AiAssistResult

    /** The caller is not in the conversation (`ai-not-participant`). */
    data object NotParticipant : AiAssistResult

    /** The pairing behind the conversation has ended (`ai-pairing-not-live`). */
    data object PairingNotLive : AiAssistResult

    /** Anything else, including an answer with no text in it. */
    data object Failed : AiAssistResult
}

/**
 * The client half of the `aiAssist` callable (region `europe-west3`). The model runs server-side;
 * the client holds no key and sends only what each task names.
 */
interface AiAssistRepository {

    /**
     * A draft reply for [conversationId]. The server reads the thread's last messages itself — the
     * client sends **no message text**, only the thread id and, when the parent has started typing,
     * [draftHint].
     *
     * @param locale The reader's language tag, e.g. `cs` or `de-AT`; the draft is written in it
     * @param draftHint What the parent has typed so far, or null
     */
    suspend fun suggestReply(conversationId: String, locale: String, draftHint: String?): AiAssistResult

    /**
     * A short narrative over a month's figures, which the client computed
     * ([com.coparently.app.domain.review.MonthStatsWire]).
     *
     * @param stats The figures, as plain maps, numbers and strings
     */
    suspend fun summarizeMonth(month: YearMonth, locale: String, stats: Map<String, Any>): AiAssistResult
}
