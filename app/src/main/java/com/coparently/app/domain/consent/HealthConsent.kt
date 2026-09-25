package com.coparently.app.domain.consent

/**
 * The wording version of the child-health consent dialog.
 *
 * **Bump this whenever the dialog's wording changes** (the `health_consent_dialog_*` strings in
 * `health_consent_strings.xml`, in any of the five locales). A parent whose stored
 * [HealthConsent.version] is lower agreed to different words, so [isCurrent] reads their consent
 * as absent and the medical section locks until they are asked again. That is what makes the
 * consent demonstrable (GDPR Art. 7(1)): a stored version names exactly which text the parent
 * agreed to.
 */
const val HEALTH_CONSENT_VERSION = 1

/**
 * A parent's explicit consent to entering their child's health details (GDPR Art. 9(2)(a)).
 *
 * Covers a child's medical profile in the widest sense: allergies, medications, conditions, blood
 * group, vaccinations, the free-text medical notes and medical photographs. It is a fact about the
 * **parent who gave it**, stored on their own profile — Room `users.healthConsentVersion` and
 * `users.healthConsentAtMillis`, Firestore `users/{uid}.healthDataConsent` — never on the child
 * record: each parent's entries rest on their own consent, so a withdrawal clears only the records
 * that parent created.
 *
 * @property version The [HEALTH_CONSENT_VERSION] of the wording the parent agreed to
 * @property atMillis When they agreed, epoch millis on the agreeing device's clock
 */
data class HealthConsent(
    val version: Int,
    val atMillis: Long
)

/**
 * Whether this consent covers the dialog as it is worded today.
 *
 * Null (never given, or withdrawn) and a stale version are both "no": a parent asked under older
 * wording is asked again.
 */
fun HealthConsent?.isCurrent(): Boolean = this != null && version >= HEALTH_CONSENT_VERSION
