package com.coparently.app.presentation.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** `mailtoUri` percent-encodes the subject and body as UTF-8 and keeps the address readable. */
class MailtoUriTest {

    @Test
    fun `a subject and body are percent-encoded and a line break is CRLF`() {
        assertEquals(
            "mailto:support+app@coplanly.app?subject=Report%20%26%20more%3F&body=Line%201%0D%0A%D0%96%20%232",
            mailtoUri("support+app@coplanly.app", "Report & more?", "Line 1\nЖ #2"),
        )
    }

    @Test
    fun `an empty subject and body leave the keys in place`() {
        assertEquals("mailto:a@b.cz?subject=&body=", mailtoUri("a@b.cz", "", ""))
    }
}
