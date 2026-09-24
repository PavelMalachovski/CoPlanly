package com.coparently.app.domain.parentingplan

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The citation's wire form (MON-21): `"p1|<questionId>|<16 hex>"`, a missing key read as no
 * citation, and anything else kept verbatim rather than dropped — a newer build's citation must
 * survive a swap write from this one.
 */
class PlanCitationCodecTest {

    @Test
    fun `a citation round-trips through its wire form`() {
        val citation = PlanCitation("holidays_school", "0123456789abcdef")

        val wire = PlanCitationCodec.encode(citation)

        assertEquals("p1|holidays_school|0123456789abcdef", wire)
        assertEquals(DecodedCitation.Readable(citation), PlanCitationCodec.decode(wire))
    }

    @Test
    fun `a missing or blank citation is no citation, not an error`() {
        assertNull(PlanCitationCodec.decode(null))
        assertNull(PlanCitationCodec.decode(""))
        assertNull(PlanCitationCodec.decode("   "))
    }

    @Test
    fun `a later format is kept verbatim and decides nothing`() {
        val wire = "p2|care_weekday|0123456789abcdef|extra"

        assertEquals(DecodedCitation.Unreadable(wire), PlanCitationCodec.decode(wire))
    }

    @Test
    fun `a damaged citation is unreadable rather than half-read`() {
        listOf(
            "p1|care_weekday",
            "p1|care_weekday|0123",
            "p1|care_weekday|0123456789ABCDEF",
            "p1|Care Weekday|0123456789abcdef",
            "p1||0123456789abcdef"
        ).forEach { wire ->
            assertEquals(DecodedCitation.Unreadable(wire), PlanCitationCodec.decode(wire), wire)
        }
    }

    @Test
    fun `the hash is the first sixteen hex characters of SHA-256`() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ba7816bf8f01cfea", PlanCitationCodec.hashOf("abc"))
        // UTF-8, so a Czech answer hashes the same on every phone.
        assertEquals(PlanCitationCodec.hashOf("Střídavá péče"), PlanCitationCodec.hashOf("Střídavá péče"))
        assertTrue(PlanCitationCodec.hashOf("Střídavá péče") != PlanCitationCodec.hashOf("Stridava pece"))
    }

    @Test
    fun `every citation this build writes fits the rule's bound`() {
        val longest = PlanScheduleLink.SCHEDULE_QUESTIONS.keys.maxBy { it.length }
        val wire = PlanCitationCodec.encode(PlanCitation(longest, PlanCitationCodec.hashOf("x")))

        assertTrue(wire.length <= PlanCitationCodec.MAX_WIRE_LENGTH)
    }

    @Test
    fun `the cited question is read from a readable citation only`() {
        assertEquals("care_weekday", PlanCitationCodec.questionIdOf("p1|care_weekday|0123456789abcdef"))
        assertNull(PlanCitationCodec.questionIdOf(null))
        assertNull(PlanCitationCodec.questionIdOf("p2|care_weekday|0123456789abcdef|extra"))
    }
}
