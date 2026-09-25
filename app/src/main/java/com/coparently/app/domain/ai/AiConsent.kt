package com.coparently.app.domain.ai

/**
 * The wording version of the AI-assist consent dialog.
 *
 * **Bump this whenever the dialog's wording changes** (`ai_consent_dialog_*` in `ai_strings.xml`,
 * in any of the five locales), and the server's `AI_CONSENT_VERSION` with it. A stored version
 * below this one agreed to different words: [isCurrent] reads it as absent and the parent is asked
 * again, which is what makes the consent demonstrable — the same rule as `HEALTH_CONSENT_VERSION`.
 */
const val AI_CONSENT_VERSION = 1

/**
 * A parent's consent to sending what an AI request needs — a thread's last messages for a reply
 * draft, a month's figures for a summary — to the server-side model.
 *
 * Stored on the parent's own profile as `users/{uid}.aiConsent = {version, grantedAt}`, where
 * `grantedAt` is the **server's** timestamp; withdrawing deletes the field. The server refuses a
 * request without a current one (`ai-consent-required`), so this copy only decides whether to ask
 * before calling, never whether a call is allowed.
 *
 * @property version The [AI_CONSENT_VERSION] of the wording the parent agreed to
 * @property grantedAtMillis When the server recorded it, or null while that write is still pending
 *   (a consent given on this device a moment ago, before the server timestamp has come back)
 */
data class AiConsent(
    val version: Int,
    val grantedAtMillis: Long?
)

/** Whether this consent covers the dialog as worded today; null (never given, withdrawn) is no. */
fun AiConsent?.isCurrent(): Boolean = this != null && version >= AI_CONSENT_VERSION
