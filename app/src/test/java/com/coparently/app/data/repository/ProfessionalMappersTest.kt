package com.coparently.app.data.repository

import com.coparently.app.domain.professionals.ProfessionalRole
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Decoding a `professional_grants` document: drop rather than guess (MON-18). */
class ProfessionalMappersTest {

    private fun stored(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "familyId" to "a__b",
        "familyParents" to listOf("a", "b"),
        "proUid" to "pro",
        "role" to "lawyer",
        "name" to "Mgr. Novák",
        "invitedBy" to "a",
        "grantedAtMillis" to 10L,
        "expiresAtMillis" to 20L,
        "consents" to mapOf("a" to 10L),
        "parentNames" to mapOf("a" to "Alice", "b" to "Bob"),
        "parentSlots" to mapOf("a" to "mom", "b" to "dad")
    ) + overrides

    @Test
    fun `a whole document decodes`() {
        val grant = assertNotNull(ProfessionalMappers.grantFrom("a__b__pro", stored()))
        assertEquals(ProfessionalRole.LAWYER, grant.role)
        assertEquals(mapOf("a" to 10L), grant.consents)
        assertEquals("Alice", grant.parentNames["a"])
    }

    @Test
    fun `no end, no family, no professional or a wrong-sized family is not a grant`() {
        assertNull(ProfessionalMappers.grantFrom("x", stored("expiresAtMillis" to null)))
        assertNull(ProfessionalMappers.grantFrom("x", stored("expiresAtMillis" to 0L)))
        assertNull(ProfessionalMappers.grantFrom("x", stored("familyId" to "")))
        assertNull(ProfessionalMappers.grantFrom("x", stored("proUid" to null)))
        assertNull(ProfessionalMappers.grantFrom("x", stored("familyParents" to listOf("a"))))
    }

    @Test
    fun `only the family's own parents count as consent`() {
        val grant = ProfessionalMappers.grantFrom(
            "x",
            stored("consents" to mapOf("a" to 1L, "pro" to 2L, "b" to "yes"))
        )
        assertEquals(mapOf("a" to 1L), grant?.consents)
    }
}
