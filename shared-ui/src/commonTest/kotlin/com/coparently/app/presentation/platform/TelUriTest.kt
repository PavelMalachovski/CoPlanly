package com.coparently.app.presentation.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `telUri` keeps digits and a leading plus, and refuses a text with no digit. */
class TelUriTest {

    @Test
    fun `spaces and dashes are dropped and a leading plus is kept`() {
        assertEquals("tel:+420777123456", telUri("+420 777-123 456"))
    }

    @Test
    fun `brackets are dropped`() {
        assertEquals("tel:7771234", telUri("777 (12) 34"))
    }

    @Test
    fun `a plus inside the number is dropped`() {
        assertEquals("tel:1234", telUri("12+34"))
    }

    @Test
    fun `a text with no digit gives nothing to dial`() {
        assertNull(telUri("abc"))
    }
}
