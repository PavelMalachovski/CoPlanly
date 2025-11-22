package com.coparently.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encryption and decryption of sensitive data using Android Keystore.
 * Uses AES-256-GCM encryption for maximum security.
 *
 * Key features:
 * - AES-256 encryption with GCM mode
 * - Keys stored in Android Keystore (hardware-backed when available)
 * - 12-byte IV for GCM mode
 * - 128-bit authentication tag
 */
@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val keyAlias = "CoParentlyEncryptionKey"

    /**
     * Gets the encryption key from Keystore or creates a new one if it doesn't exist.
     */
    private fun getOrCreateKey(): SecretKey {
        return if (keyStore.containsAlias(keyAlias)) {
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            createKey()
        }
    }

    /**
     * Creates a new AES-256 key in the Android Keystore.
     */
    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setUserAuthenticationRequired(false)
            // For production, consider enabling:
            // setUserAuthenticationRequired(true)
            // setUserAuthenticationValidityDurationSeconds(30)
        }.build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the given data using AES-256-GCM.
     *
     * @param data The plaintext string to encrypt
     * @return Base64-encoded string containing IV + encrypted data
     * @throws Exception if encryption fails
     */
    fun encrypt(data: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            // Combine IV and encrypted data
            // Format: [IV (12 bytes)][Encrypted Data (variable)]
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts the given encrypted data.
     *
     * @param encryptedData Base64-encoded string containing IV + encrypted data
     * @return The decrypted plaintext string
     * @throws Exception if decryption fails
     */
    fun decrypt(encryptedData: String): String {
        try {
            val combined = Base64.decode(encryptedData, Base64.DEFAULT)

            // Extract IV and encrypted data
            // GCM IV is 12 bytes
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), gcmParameterSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt data", e)
        }
    }

    /**
     * Encrypts a list of strings.
     *
     * @param dataList List of plaintext strings
     * @return List of encrypted strings
     */
    fun encryptList(dataList: List<String>): List<String> {
        return dataList.map { encrypt(it) }
    }

    /**
     * Decrypts a list of encrypted strings.
     *
     * @param encryptedDataList List of encrypted strings
     * @return List of decrypted plaintext strings
     */
    fun decryptList(encryptedDataList: List<String>): List<String> {
        return encryptedDataList.map { decrypt(it) }
    }

    /**
     * Checks if the encryption key exists in the Keystore.
     */
    fun hasKey(): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Deletes the encryption key from the Keystore.
     * WARNING: This will make all encrypted data unrecoverable!
     */
    fun deleteKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 12 bytes for GCM
        private const val GCM_TAG_LENGTH = 128 // 128-bit authentication tag
    }
}

/**
 * Exception thrown when encryption or decryption fails.
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
