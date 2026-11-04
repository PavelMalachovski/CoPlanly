package com.coparently.app.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ConversationKeyTest {

    @Test
    fun `both argument orders produce the same id`() {
        assertEquals(
            ConversationKey.of("uidA", "uidB"),
            ConversationKey.of("uidB", "uidA")
        )
    }

    @Test
    fun `the id is stable across calls`() {
        assertEquals(ConversationKey.of("uidA", "uidB"), ConversationKey.of("uidA", "uidB"))
    }

    @Test
    fun `different pairs do not collide`() {
        assertNotEquals(ConversationKey.of("uidA", "uidB"), ConversationKey.of("uidA", "uidC"))
    }

    @Test
    fun `a pair sharing a prefix does not collide with another`() {
        // "ab" + "c" and "a" + "bc" must not both become "abc".
        assertNotEquals(ConversationKey.of("ab", "c"), ConversationKey.of("a", "bc"))
    }

    @Test
    fun `pairing a user with themselves is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ConversationKey.of("uidA", "uidA")
        }
    }

    @Test
    fun `a blank uid is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ConversationKey.of("uidA", "  ")
        }
    }

    @Test
    fun `matches the literal id firestore rules' canonicalPairId derives for the pair`() {
        // The drift pin's Kotlin-side half. `firestore.rules`' `canonicalPairId`
        // re-derives this exact sorted-join formula in Rules, to constrain the
        // legacy-conversation merge's message re-point to the canonical conversation only (see
        // `conversations-messages.test.js`'s "computes the same canonical id..." case, which
        // asserts the matching literal succeeds against the real rule in the emulator). If
        // either side's formula ever changes, only one of the two pins moves and the other's
        // literal assertion catches it — the two derivations cannot silently disagree.
        //
        // `canonicalPairId` also gates `custody_models` now (renamed from
        // `canonicalConversationId` when that collection started sharing it); this test's name
        // and this comment were updated to match so the pin still points at a symbol that
        // exists.
        assertEquals("alice-uid__bob-uid", ConversationKey.of("alice-uid", "bob-uid"))
    }

    @Test
    fun `a uid containing the separator is rejected instead of silently colliding`() {
        // Without this guard, of("x__y", "z") and of("x", "y__z") would both join to
        // "x__y__z" — two different pairs producing the same id.
        assertFailsWith<IllegalArgumentException> {
            ConversationKey.of("x__y", "z")
        }
        assertFailsWith<IllegalArgumentException> {
            ConversationKey.of("x", "y__z")
        }
    }
}
