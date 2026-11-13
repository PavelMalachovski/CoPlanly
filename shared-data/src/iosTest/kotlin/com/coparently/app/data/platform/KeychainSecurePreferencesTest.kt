package com.coparently.app.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SecurePreferencesContractTest] over the real Keychain (MON-24 phase 2, Task 6), on an item of the
 * test's own, never the app's.
 *
 * **Only on a booted simulator** (MON-24 phase 3, Task 5). Kotlin spawns a simulator test binary
 * `--standalone` by default, outside a booted device's services, and every Keychain call there
 * answers `errSecNotAvailable` (−25291). The `ios-app` CI job runs this module's tests on the
 * simulator it booted (`COPLANLY_SIMULATOR_UDID`), from a binary carrying simulated Keychain
 * entitlements (`gradle/ios-test-entitlements.plist`), where Firebase Auth saves its user too; there
 * these cases run. Elsewhere (`shared-ios`, a laptop without the variable)
 * `shared-data/build.gradle.kts` leaves the class out of the run rather than letting it return
 * early: a test that passed on −25291 would claim a Keychain nobody exercised. The contract also
 * runs everywhere over an in-memory store (`SealedSecurePreferencesContractTest`). What only a phone
 * shows — the item surviving a relaunch and readable after the first unlock — stays a device check.
 */
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
