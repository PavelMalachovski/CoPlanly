package com.coparently.app.domain.guests

/**
 * What [PairingFunctions.acceptGuestInvitation] returns on success.
 *
 * @property childInfoId The one child record the caller may now read. Exactly one: a guest is
 *   invited to a child, not to a family.
 * @property expiresAtMillis When that access ends, as epoch milliseconds. Required, unlike
 *   [AcceptInvitationResult.role]: a missing `role` costs a best-effort local migration,
 *   while a missing expiry would be shown to the guest as access that never ends.
 */
data class AcceptGuestResult(val childInfoId: String, val expiresAtMillis: Long)
