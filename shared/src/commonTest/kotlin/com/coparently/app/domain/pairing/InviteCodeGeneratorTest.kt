package com.coparently.app.domain.pairing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteCodeGeneratorTest {

    @Test
    fun `generated code has the required length`() {
        repeat(200) {
            assertEquals(InviteCodeGenerator.LENGTH, InviteCodeGenerator.generate().length)
        }
    }

    @Test
    fun `generated code never contains visually ambiguous characters`() {
        val forbidden = setOf('O', '0', 'I', '1', 'L')
        repeat(2_000) {
            val code = InviteCodeGenerator.generate()
            assertTrue(
                code.none { it in forbidden },
                "code $code contains a forbidden character"
            )
        }
    }

    @Test
    fun `generated code uses only the published alphabet`() {
        repeat(2_000) {
            val code = InviteCodeGenerator.generate()
            assertTrue(code.all { it in InviteCodeGenerator.ALPHABET })
        }
    }

    @Test
    fun `generation is deterministic for a seeded random`() {
        assertEquals(
            InviteCodeGenerator.generate(Random(42)),
            InviteCodeGenerator.generate(Random(42))
        )
    }

    @Test
    fun `isValid accepts a well formed code and rejects everything else`() {
        assertTrue(InviteCodeGenerator.isValid("4F7K2M"))
        assertFalse(InviteCodeGenerator.isValid("4F7K2"), "wrong length")
        assertFalse(InviteCodeGenerator.isValid("4f7k2m"), "lowercase")
        assertFalse(InviteCodeGenerator.isValid("4F7K2O"), "ambiguous char")
        assertFalse(InviteCodeGenerator.isValid(""), "empty")
    }
}
