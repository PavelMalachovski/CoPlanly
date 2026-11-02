package com.coparently.app.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SecurePreferencesContractTest] over the real Keychain of the simulator (MON-24 phase 2, Task 6),
 * on an item of the test's own, never the app's.
 */
class KeychainSecurePreferencesTest : SecurePreferencesContractTest() {

    private val storage = KeychainTextStore(service = "app.coplanly.test.preferences", account = "contract")

    override fun open(): SecurePreferences = SealedSecurePreferences(storage)

    override fun wipe() = storage.delete()

    @Test
    fun `the item holds the text it was given until it is deleted`() {
        storage.write("first")
        storage.write("second — ключ")
        assertEquals("second — ключ", storage.read())

        storage.delete()
        assertNull(storage.read())
    }
}
