@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * One generic-password item in the Keychain, named by [service] and [account], holding a text
 * (MON-24 phase 2, Task 6): the iPhone's [SealedTextStore].
 *
 * The Keychain seals it, as the Android Keystore key seals `secure_prefs.bin`. It is readable after
 * the phone's first unlock since it started (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), so
 * a background sync can read the tokens, and it never leaves this device: no iCloud Keychain, no
 * backup restored onto another phone — the reasons Android's file sits under `no_backup`.
 */
class KeychainTextStore(
    private val service: String = APP_SERVICE,
    private val account: String = APP_ACCOUNT
) : SealedTextStore {

    // Only "not found" means nothing is stored: any other status — the item locked before the first
    // unlock, the Keychain unavailable, a missing entitlement — is reported as unreadable, and the
    // store keeps the item rather than starting again over it.
    override fun read(): StoredText = memScoped {
        val query = dictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to retained(service),
            kSecAttrAccount to retained(account),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val result = alloc<CFTypeRefVar>()
        when (val status = SecItemCopyMatching(query, result.ptr)) {
            errSecSuccess -> StoredText.Present(
                (CFBridgingRelease(result.value) as NSData).toByteArray().decodeToString()
            )
            errSecItemNotFound -> StoredText.Absent
            else -> StoredText.Unreadable("OSStatus $status")
        }
    }

    override fun write(text: String) = memScoped {
        val data = retained(text.encodeToByteArray().toNSData())
        val updated = SecItemUpdate(identity(), dictionary(kSecValueData to data))
        val status = if (updated == errSecItemNotFound) {
            SecItemAdd(
                dictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to retained(service),
                    kSecAttrAccount to retained(account),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    kSecValueData to data
                ),
                null
            )
        } else {
            updated
        }
        if (status != errSecSuccess) throw KeychainException("write", status)
    }

    override fun delete() = memScoped {
        val status = SecItemDelete(identity())
        if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException("delete", status)
    }

    /** The query that names this item. */
    private fun MemScope.identity(): CFDictionaryRef? = dictionary(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to retained(service),
        kSecAttrAccount to retained(account)
    )

    /** A dictionary released when the scope ends; it retains its own keys and values. */
    private fun MemScope.dictionary(vararg entries: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(
            null,
            entries.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        defer { CFRelease(dictionary) }
        return dictionary
    }

    /** [value] as a Core Foundation object, released when the scope ends. */
    private fun MemScope.retained(value: Any): CFTypeRef? {
        val reference = CFBridgingRetain(value)
        defer { CFRelease(reference) }
        return reference
    }

    /** Where the app's own preferences live. */
    companion object {
        /** The app's preference item: its service. */
        const val APP_SERVICE = "app.coplanly.preferences"

        /** The app's preference item: its account. */
        const val APP_ACCOUNT = "secure_prefs"
    }
}

/** A Keychain call that answered [status], an `OSStatus`, rather than success. */
class KeychainException(operation: String, val status: Int) :
    IllegalStateException("Keychain $operation failed: OSStatus $status")

/** The app's [SecurePreferences] on the iPhone: one Keychain item (see [KeychainTextStore]). */
fun keychainSecurePreferences(): SecurePreferences = SealedSecurePreferences(KeychainTextStore())

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

private fun NSData.toByteArray(): ByteArray {
    val copy = ByteArray(length.toInt())
    if (copy.isNotEmpty()) copy.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return copy
}
