package com.coparently.app.domain.professionals

/**
 * What `acceptProfessionalInvitation` returns (MON-18).
 *
 * @property grantId The grant's document id, `{familyId}__{proUid}`.
 * @property expiresAtMillis When the access ends, epoch millis, always positive. The access has
 *   not begun: it opens only once both parents have consented.
 */
data class AcceptProfessionalResult(
    val grantId: String,
    val expiresAtMillis: Long
)
