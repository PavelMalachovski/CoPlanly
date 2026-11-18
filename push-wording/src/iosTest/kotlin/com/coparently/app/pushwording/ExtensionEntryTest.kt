package com.coparently.app.pushwording

import platform.Foundation.NSBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The notification extension's entry ([wordPush], here over the generated strings' bundle,
 * `PushStringsLocation`): it words a push for the account the app shared, in the language the app
 * shared, and nothing else — a null the extension answers by delivering the server's neutral
 * fallback (design §2 decision 4).
 */
class ExtensionEntryTest {

    private val bundle: NSBundle =
        requireNotNull(NSBundle.bundleWithPath(PushStringsLocation.DIR)) {
            "no strings at ${PushStringsLocation.DIR}"
        }

    private val expense = mapOf(
        "type" to "expense_added",
        "targetUserId" to READER,
        "familyId" to "a__b",
        "actorName" to "Bob",
        "subject" to "Lebensmittel",
        "amount" to "12.50",
        "currency" to "EUR",
    )

    @Test
    fun anExpenseIsWordedInTheSharedLanguage() {
        val text = assertNotNull(wordPushFrom(bundle, expense, READER, "de"))
        assertEquals("Neue Ausgabe", text.title)
        assertTrue(text.body.startsWith("Bob hat Lebensmittel hinzugefügt: "), text.body)
        assertTrue("12,50" in text.body, text.body)
    }

    @Test
    fun aPushForAnotherUidIsNotWorded() {
        assertNull(wordPushFrom(bundle, expense, "someone-else", "de"))
    }

    @Test
    fun aPushAddressedToNobodyIsNotWorded() {
        assertNull(wordPushFrom(bundle, expense - "targetUserId", READER, "de"))
    }

    @Test
    fun anUnknownTypeIsNotWorded() {
        assertNull(wordPushFrom(bundle, expense + ("type" to "a_type_from_a_newer_build"), READER, "de"))
        assertNull(wordPushFrom(bundle, expense - "type", READER, "de"))
    }

    @Test
    fun aBlankSharedUidIsNotWorded() {
        assertNull(wordPushFrom(bundle, expense + ("targetUserId" to ""), "", "de"))
        assertNull(wordPushFrom(bundle, expense, " ", "de"))
        assertNull(wordPushFrom(bundle, expense, null, "de"))
    }

    @Test
    fun noSharedLanguageReadsEnglish() {
        val text = assertNotNull(wordPushFrom(bundle, expense + ("subject" to "Groceries"), READER, null))
        assertEquals("New expense", text.title)
        assertTrue(text.body.startsWith("Bob added Groceries: "), text.body)
    }

    @Test
    fun aChatMessageIsRelayedForTheSharedUid() {
        val message = mapOf(
            "type" to "chat_message",
            "targetUserId" to READER,
            "actorName" to "Bob",
            "preview" to "See you at five",
        )
        assertEquals(PushText("Bob", "See you at five"), wordPushFrom(bundle, message, READER, "en"))
    }

    private companion object {
        const val READER = "uid-alice"
    }
}
