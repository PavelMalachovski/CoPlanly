package com.coparently.app.data.platform

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SecurePreferencesContractTest] over the real Keychain (MON-24 phase 2, Task 6), on an item of the
 * test's own, never the app's.
 *
 * **Ignored until phase 3, on purpose.** The Keychain exists only for a process an app hosts: the
 * Kotlin/Native test binary `iosSimulatorArm64Test` spawns on the simulator has none, and every
 * Keychain call there answers `errSecNotAvailable` (−25291) — an entitlement would not change that.
 * These cases run once the iPhone app's XCTest host exists (ROADMAP MON-24, before phase 3). Until
 * then the contract still runs under Kotlin/Native through the real code path:
 * `SealedSecurePreferencesContractTest` and `SealedSecurePreferencesTest` (commonTest) drive
 * `SealedSecurePreferences` and `PreferenceBlobCodec` over an in-memory store on the simulator too.
 * A test that passed by returning early on −25291 would claim a Keychain nobody exercised.
 */
@Ignore
class KeychainSecurePreferencesTest : SecurePreferencesContractTest() {

    private val storage = KeychainTextStore(service = "app.coplanly.test.preferences", account = "contract")

    override fun open(): SecurePreferences = SealedSecurePreferences(storage)

    override fun wipe() = storage.delete()

    @Test
    fun `the item holds the text it was given until it is deleted`() {
        storage.write("first")
        storage.write("second — ключ")
        assertEquals(StoredText.Present("second — ключ"), storage.read())

        storage.delete()
        assertEquals(StoredText.Absent, storage.read())
    }
}
