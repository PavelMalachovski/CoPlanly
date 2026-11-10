package com.coparently.app.data.platform

/** [SecurePreferencesContractTest] over [SealedSecurePreferences] on [MemoryTextStore]. */
class SealedSecurePreferencesContractTest : SecurePreferencesContractTest() {

    private val storage = MemoryTextStore()

    override fun open(): SecurePreferences = SealedSecurePreferences(storage)

    override fun wipe() = storage.delete()
}

/** A [SealedTextStore] in memory: the text a Keychain item would hold, and nothing sealed. */
internal class MemoryTextStore : SealedTextStore {

    /** What is stored, or null. */
    var text: String? = null

    /** How many writes reached the store. */
    var writes = 0

    override fun read(): StoredText = text?.let { StoredText.Present(it) } ?: StoredText.Absent

    override fun write(text: String) {
        this.text = text
        writes++
    }

    override fun delete() {
        text = null
    }
}
