package com.coparently.app.domain.professionals

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MON-18's two asymmetries, as the app states them: activation needs **both** parents, and the
 * grant always ends. The rule (`professionalGrantActive`) and the sweep must agree with every case.
 */
class ProfessionalGrantPolicyTest {

    private val now = 1_000_000L

    private fun grant(consents: Map<String, Long>, expiresAtMillis: Long = now + 1) = ProfessionalGrant(
        id = "a__b__pro",
        familyId = "a__b",
        familyParents = listOf("a", "b"),
        proUid = "pro",
        role = ProfessionalRole.MEDIATOR,
        name = "Mediator",
        invitedBy = "a",
        grantedAtMillis = 1L,
        expiresAtMillis = expiresAtMillis,
        consents = consents
    )

    @Test
    fun `one consent opens nothing`() {
        assertFalse(ProfessionalGrantPolicy.isActive(grant(mapOf("a" to 1L)), now))
    }

    @Test
    fun `both consents open it until the end`() {
        assertTrue(ProfessionalGrantPolicy.isActive(grant(mapOf("a" to 1L, "b" to 2L)), now))
    }

    @Test
    fun `a grant ending now is already expired, like the rule's strict comparison`() {
        val both = mapOf("a" to 1L, "b" to 2L)
        assertFalse(ProfessionalGrantPolicy.isActive(grant(both, expiresAtMillis = now), now))
        assertEquals(
            ProfessionalGrantStatus.EXPIRED,
            ProfessionalGrantPolicy.statusFor(grant(both, expiresAtMillis = now), "a", now)
        )
    }

    @Test
    fun `a missing end is expired, never unlimited`() {
        assertTrue(ProfessionalGrantPolicy.isExpired(grant(mapOf("a" to 1L, "b" to 2L), 0L), now))
    }

    @Test
    fun `a consent from somebody who is not a parent does not count`() {
        assertFalse(ProfessionalGrantPolicy.isActive(grant(mapOf("a" to 1L, "pro" to 2L)), now))
    }

    @Test
    fun `the status names whose consent is missing, from each side`() {
        val onlyA = grant(mapOf("a" to 1L))
        assertEquals(ProfessionalGrantStatus.WAITING_FOR_YOU, ProfessionalGrantPolicy.statusFor(onlyA, "b", now))
        assertEquals(
            ProfessionalGrantStatus.WAITING_FOR_CO_PARENT,
            ProfessionalGrantPolicy.statusFor(onlyA, "a", now)
        )
        // The professional can do nothing about it, so is never told it waits for them.
        assertEquals(
            ProfessionalGrantStatus.WAITING_FOR_CO_PARENT,
            ProfessionalGrantPolicy.statusFor(onlyA, "pro", now)
        )
    }

    @Test
    fun `no offered length reaches the ceiling`() {
        ProfessionalAccessDuration.entries.forEach {
            assertTrue(it.days < ProfessionalGrantPolicy.MAX_DURATION_DAYS, "$it")
        }
    }

    @Test
    fun `an unknown stored role reads as other rather than failing`() {
        assertEquals(ProfessionalRole.OTHER, ProfessionalRole.fromWire("astrologer"))
        assertEquals(ProfessionalRole.GUARDIAN_AD_LITEM, ProfessionalRole.fromWire("guardian_ad_litem"))
    }

    @Test
    fun `a parent's name is found by slot`() {
        val named = grant(emptyMap()).copy(
            parentNames = mapOf("a" to "Alice", "b" to "Bob"),
            parentSlots = mapOf("a" to "mom", "b" to "dad")
        )
        assertEquals("Bob", named.parentNameForSlot("dad"))
        assertEquals(null, grant(emptyMap()).parentNameForSlot("mom"))
    }
}
