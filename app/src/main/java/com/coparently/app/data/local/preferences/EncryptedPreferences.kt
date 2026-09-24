package com.coparently.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.coparently.app.data.security.EncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted SharedPreferences wrapper for secure storage.
 *
 * Holds the Google OAuth **refresh token**, access token, ID token and the signed-in address
 * (see the `KEY_*` constants) — the refresh token being a credential that grants a bearer
 * standing access to the user's Google Calendar until it is revoked.
 *
 * **This never degrades to unencrypted on-disk storage, and the degradation it used to perform
 * is the reason this class is written the way it is.** Every failure branch used to end in
 * `context.getSharedPreferences("encrypted_prefs", MODE_PRIVATE)` — the *same file name* the
 * encrypted store uses — so a single `EncryptedSharedPreferences.create` failure wrote the
 * refresh token to `/data/data/<pkg>/shared_prefs/encrypted_prefs.xml` in clear text, and said
 * so only in a `Log.w` nobody reads. Two things made that permanent rather than momentary:
 *
 *  * the recovery deleted `encrypted_prefs.xml` via [Context.deleteFile], which resolves under
 *    `files/`, while SharedPreferences live under `shared_prefs/` — so the corrupt keyset was
 *    never actually removed and every later launch failed identically; and
 *  * the fallback shared the encrypted store's file, mixing AES256_SIV key names and
 *    ciphertext values with plain ones, so the store could not read its own earlier writes and
 *    the app simply re-issued the tokens and wrote those in clear text beside them.
 *
 * The keysets `EncryptedSharedPreferences` derives live inside that same preferences file, so
 * clearing the file is what a genuine recovery looks like: it costs the stored tokens (the user
 * signs in to Google Calendar again) and buys back an encrypted store. If even that fails, the
 * store is held [InMemorySharedPreferences] — the behaviour the old "ultimate fallback" comment
 * claimed but did not implement. Tokens then live for the process and no further, which is the
 * correct trade for a credential: an inconvenience is recoverable, a plaintext refresh token on
 * disk is not.
 *
 * **Where it lives now (SEC-5).** The store used to be `androidx.security:security-crypto`'s
 * `EncryptedSharedPreferences`, on `1.1.0-alpha06` — a line Google stopped developing and later
 * deprecated outright. It is now one file, `no_backup/secure_prefs.bin`: the whole map, encoded by
 * [PreferenceBlobCodec] and sealed with AES-256-GCM under the Android Keystore key
 * [EncryptionManager] already holds for the database passphrase (item 20), written to a temporary
 * file and renamed into place so a killed process leaves the old version or the new one, never
 * half of either. `no_backup`, because a restored copy could not be opened on another device's
 * Keystore anyway.
 *
 * **The library stays for one job: reading the old store once.** On the first launch of this
 * build, whatever `encrypted_prefs` holds — the Google tokens, the telemetry answer, the
 * parent-slot markers [clear] protects — is copied into the new file, and the old file is deleted
 * only after the new one has been written. It is never written to again. When no install older
 * than this build can remain, the dependency and [readLegacyStore] go together.
 *
 * The same rules hold as before: a sealed file that cannot be opened is deleted and the store
 * starts empty (the user re-authorises Calendar), and a device that cannot seal anything keeps the
 * store in memory — never on disk in clear text.
 */
@Singleton
class EncryptedPreferences internal constructor(
    private val context: Context,
    private val cipher: StoreCipher,
    private val fileName: String = STORE_FILE,
    private val legacyName: String = LEGACY_STORE_NAME
) {

    /** The production store: the Keystore key [encryptionManager] holds, the app's own files. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        encryptionManager: EncryptionManager
    ) : this(context, KeystoreCipher(encryptionManager))

    /** Seals and opens the store's text; [EncryptionManager] in production. */
    interface StoreCipher {
        /** [plain], sealed. */
        fun seal(plain: String): String

        /** The plaintext of [sealed]; throws when it cannot be opened. */
        fun open(sealed: String): String
    }

    /** [StoreCipher] over the Keystore key [manager] holds. */
    internal class KeystoreCipher(private val manager: EncryptionManager) : StoreCipher {
        override fun seal(plain: String): String = manager.encrypt(plain)
        override fun open(sealed: String): String = manager.decrypt(sealed)
    }

    private val store: SharedPreferences = openStore()

    /**
     * Stores an access token securely.
     */
    fun putAccessToken(token: String) {
        store.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }

    /**
     * Retrieves the stored access token.
     */
    fun getAccessToken(): String? {
        return store.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Stores a refresh token securely.
     */
    fun putRefreshToken(token: String) {
        store.edit()
            .putString(KEY_REFRESH_TOKEN, token)
            .apply()
    }

    /**
     * Retrieves the stored refresh token.
     */
    fun getRefreshToken(): String? {
        return store.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Stores token expiry time in milliseconds.
     */
    fun putTokenExpiry(expiryTimeMillis: Long) {
        store.edit()
            .putLong(KEY_TOKEN_EXPIRY, expiryTimeMillis)
            .apply()
    }

    /**
     * Retrieves the stored token expiry time.
     */
    fun getTokenExpiry(): Long? {
        val expiry = store.getLong(KEY_TOKEN_EXPIRY, -1)
        return if (expiry == -1L) null else expiry
    }

    /**
     * Stores Google Calendar ID.
     */
    fun putCalendarId(calendarId: String) {
        store.edit()
            .putString(KEY_CALENDAR_ID, calendarId)
            .apply()
    }

    /**
     * Retrieves the stored Google Calendar ID.
     */
    fun getCalendarId(): String? {
        return store.getString(KEY_CALENDAR_ID, "primary")
    }

    /**
     * Stores Google ID token (from Credential Manager).
     */
    fun putGoogleIdToken(token: String) {
        store.edit()
            .putString(KEY_GOOGLE_ID_TOKEN, token)
            .apply()
    }

    /**
     * Retrieves the stored Google ID token.
     */
    fun getGoogleIdToken(): String? {
        return store.getString(KEY_GOOGLE_ID_TOKEN, null)
    }

    /**
     * Stores user email from Google account.
     */
    fun putUserEmail(email: String) {
        store.edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    /**
     * Retrieves the stored user email.
     */
    fun getUserEmail(): String? {
        return store.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Stores dark theme preference.
     *
     * @param isDarkTheme Whether dark theme is enabled
     */
    fun putDarkTheme(isDarkTheme: Boolean) {
        store.edit()
            .putBoolean(KEY_DARK_THEME, isDarkTheme)
            .apply()
    }

    /**
     * Retrieves dark theme preference.
     * Returns null if not set (use system default).
     *
     * @return True if dark theme, false if light theme, null if system default
     */
    fun getDarkTheme(): Boolean? {
        return if (store.contains(KEY_DARK_THEME)) {
            store.getBoolean(KEY_DARK_THEME, false)
        } else {
            null // Not set, use system default
        }
    }

    /**
     * Clears dark theme preference (reverts to system default).
     */
    fun clearDarkTheme() {
        store.edit()
            .remove(KEY_DARK_THEME)
            .apply()
    }

    /**
     * Stores a boolean value.
     *
     * @param key The key name
     * @param value The boolean value
     */
    fun putBoolean(key: String, value: Boolean) {
        store.edit()
            .putBoolean(key, value)
            .apply()
    }

    /**
     * Retrieves a boolean value.
     *
     * @param key The key name
     * @param defaultValue The default value if not found
     * @return The boolean value
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return store.getBoolean(key, defaultValue)
    }

    /**
     * Stores a string value.
     *
     * @param key The key name
     * @param value The string value
     */
    fun putString(key: String, value: String) {
        store.edit()
            .putString(key, value)
            .apply()
    }

    /**
     * Retrieves a string value.
     *
     * @param key The key name
     * @param defaultValue The default value if not found
     * @return The string value
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return store.getString(key, defaultValue)
    }

    /**
     * Stores event draft data as JSON string.
     * Issue 1.3: Draft saving functionality.
     */
    fun putEventDraft(draftJson: String) {
        store.edit()
            .putString(KEY_EVENT_DRAFT, draftJson)
            .apply()
    }

    /**
     * Retrieves the stored event draft.
     * Issue 1.3: Draft saving functionality.
     */
    fun getEventDraft(): String? {
        return store.getString(KEY_EVENT_DRAFT, null)
    }

    /**
     * Clears the stored event draft.
     * Issue 1.3: Draft saving functionality.
     */
    fun clearEventDraft() {
        store.edit()
            .remove(KEY_EVENT_DRAFT)
            .apply()
    }

    /**
     * Stores the unsent composer text for one conversation.
     *
     * An empty [text] removes the entry rather than storing a blank, so a thread the user
     * cleared does not keep a row forever.
     *
     * @param conversationId The thread the text was typed into.
     * @param text What is in the composer.
     */
    fun putChatDraft(conversationId: String, text: String) {
        val key = PreferenceKeys.CHAT_DRAFT_PREFIX + conversationId
        store.edit().apply {
            if (text.isEmpty()) remove(key) else putString(key, text)
        }.apply()
    }

    /**
     * The unsent composer text for one conversation, or an empty string when there is none.
     *
     * @param conversationId The thread to read the draft of.
     */
    fun getChatDraft(conversationId: String): String =
        store.getString(PreferenceKeys.CHAT_DRAFT_PREFIX + conversationId, null)
            .orEmpty()

    /**
     * Records the agreed split of a shared expense, as slot 1's share in basis points.
     *
     * @param basisPoints `0..10000`.
     */
    fun putSplitRatioBasisPoints(basisPoints: Int) {
        store.edit()
            .putInt(PreferenceKeys.SPLIT_RATIO_BASIS_POINTS, basisPoints)
            .apply()
    }

    /**
     * The agreed split, or null when the family has never agreed one.
     *
     * Null rather than "half each": "we never agreed" and "we agreed on half each" are different
     * facts, and only the caller knows which fallback belongs to it.
     */
    fun getSplitRatioBasisPoints(): Int? =
        store.getInt(PreferenceKeys.SPLIT_RATIO_BASIS_POINTS, -1)
            .takeIf { it >= 0 }

    /**
     * Records which slot the cached share belongs to, so it can be re-anchored later.
     *
     * @param slot `"mom"` or `"dad"`, or null to forget — which is what a paired write does,
     *   because from then on the pair's document is the record and the cache merely mirrors it.
     */
    fun putSplitRatioSlot(slot: String?) {
        store.edit()
            .apply {
                if (slot == null) {
                    remove(PreferenceKeys.SPLIT_RATIO_SLOT)
                } else {
                    putString(PreferenceKeys.SPLIT_RATIO_SLOT, slot)
                }
            }
            .apply()
    }

    /** The slot the cached share was captured under, or null when it was never recorded. */
    fun getSplitRatioSlot(): String? =
        store.getString(PreferenceKeys.SPLIT_RATIO_SLOT, null)

    /**
     * Stores the app-wide default currency.
     *
     * @param code ISO 4217 currency code, e.g. "CZK"
     */
    fun putDefaultCurrency(code: String) {
        store.edit()
            .putString(KEY_DEFAULT_CURRENCY, code)
            .apply()
    }

    /**
     * Retrieves the stored default currency code.
     *
     * @return The ISO 4217 code, or null when the user has never had one resolved
     */
    fun getDefaultCurrency(): String? {
        return store.getString(KEY_DEFAULT_CURRENCY, null)
    }

    /**
     * Clears stored preferences — **except** the per-user parent-slot markers
     * ([PreferenceKeys.PARENT_SLOT_MARKER_PREFIX]).
     *
     * This is no longer literally "all", and that is deliberate, not an oversight: this method
     * is reached from the app's own Sign out (`SettingsScreen`'s confirm dialog runs
     * `SyncViewModel.signOut` — which calls this via `CredentialManagerService`/
     * immediately before `AuthStateViewModel.signOut`) as well as from
     * disconnecting Google Calendar alone (`onCalendarSignOut`). Neither of those touches Room,
     * where `users`/`events` rows deliberately survive sign-out so a returning parent's history
     * is still there. Before this exemption, signing out during the window between a
     * server-side slot backfill and this device's next sync wiped the one record
     * (`ParentSlotMigrator`'s marker) that tells the next sync a device has local history
     * needing a re-stamp — so a device with a full local history stamped in the old slot would
     * take the "nothing to do" branch on sign back in, permanently, the same damage this
     * marker exists to prevent, reintroduced through where it lives.
     *
     * A Google Calendar disconnect has no business wiping a parent-slot marker in the first
     * place — the two describe unrelated things — so this exemption is arguably correct on its
     * own terms, not merely a patch. See [PreferenceKeys.PARENT_SLOT_MARKER_PREFIX]'s own KDoc
     * for why the marker is keyed per-UID: it is what stops a second account that later signs
     * in on this device from reading the first account's now-surviving marker as its own.
     */
    fun clear() {
        val preservedMarkers = store.all
            .filterKeys { it.startsWith(PreferenceKeys.PARENT_SLOT_MARKER_PREFIX) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }

        store.edit().apply {
            clear()
            preservedMarkers.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }

    /**
     * Opens the sealed store, migrating the old one on the first launch of this build.
     *
     * Every branch ends in a store that is encrypted on disk or not on disk at all; see the class
     * KDoc for why "in memory" is the fallback and plaintext never is.
     */
    private fun openStore(): SharedPreferences {
        val file = File(context.noBackupFilesDir, fileName)
        val stored = readSealed(file)
        val legacy = if (stored == null) readLegacyStore() else null
        val values = stored ?: legacy.orEmpty()

        // Writing now doubles as the probe: a device whose Keystore cannot seal anything learns
        // it here, before a single token is handed to a store that could not keep it.
        if (!writeSealed(file, values)) {
            Log.e(TAG, "No encrypted store available; keeping preferences in memory only")
            return InMemorySharedPreferences(values)
        }
        if (legacy != null) {
            // Only now that the new file holds everything the old one did.
            deleteLegacyStore()
        }
        return InMemorySharedPreferences(values) { snapshot -> writeSealed(file, snapshot) }
    }

    /** The sealed store's entries, or null when there is no file or it cannot be opened. */
    private fun readSealed(file: File): Map<String, Any?>? {
        if (!file.exists()) return null
        return try {
            PreferenceBlobCodec.decode(cipher.open(file.readText()))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The Keystore key is gone (a lock-screen reset on some builds, a restore) or the file
            // is damaged. The file is useless either way: start again, and the user re-authorises
            // Calendar — nothing else in here is not re-derivable.
            Log.w(TAG, "Encrypted store unreadable; clearing it and re-creating", e)
            file.delete()
            null
        }
    }

    /**
     * Seals [values] into [file] through a temporary file and a rename, so a process killed
     * mid-write leaves the previous version intact.
     *
     * @return false when nothing could be written — the caller keeps the values in memory.
     */
    private fun writeSealed(file: File, values: Map<String, Any?>): Boolean = try {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, "${file.name}.part")
        partial.writeText(cipher.seal(PreferenceBlobCodec.encode(values)))
        if (!partial.renameTo(file)) throw IOException("Could not move the sealed store into place")
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.e(TAG, "Could not write the encrypted store", e)
        false
    }

    /**
     * What the pre-SEC-5 `EncryptedSharedPreferences` store holds, or null when there is none or
     * it cannot be opened (a corrupt keyset: its contents are lost, as that store's own recovery
     * would have lost them).
     *
     * Checked through a plain handle first, which reads the file without decrypting it, so a
     * fresh install never touches the old library at all.
     */
    private fun readLegacyStore(): Map<String, Any?>? {
        val present = try {
            context.getSharedPreferences(legacyName, Context.MODE_PRIVATE).all.isNotEmpty()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Could not check for the old encrypted store", e)
            false
        }
        if (!present) return null
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                legacyName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).all.toMap()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Deliberately broad: besides the checked failures, the old library's Keystore path
            // throws `ProviderException` and `IllegalStateException` on some builds, and nothing
            // about reading an old store may stop the app from opening.
            Log.e(TAG, "The old encrypted store could not be opened; its contents are lost", e)
            deleteLegacyStore()
            null
        }
    }

    /**
     * Removes the old store's file, keysets included. `deleteSharedPreferences` is API 24+ and
     * this module is minSdk 26.
     */
    private fun deleteLegacyStore() {
        try {
            context.deleteSharedPreferences(legacyName)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Could not delete the old encrypted store", e)
        }
    }

    companion object {
        private const val TAG = "EncryptedPreferences"

        /** The sealed store, under `noBackupFilesDir`. */
        private const val STORE_FILE = "secure_prefs.bin"

        /**
         * The pre-SEC-5 `EncryptedSharedPreferences` file. It held both the encrypted entries and
         * the Tink keysets wrapped by the master key, which is why deleting it removes it whole.
         */
        private const val LEGACY_STORE_NAME = "encrypted_prefs"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_GOOGLE_ID_TOKEN = "google_id_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_EVENT_DRAFT = "event_draft"
        private const val KEY_DEFAULT_CURRENCY = "default_currency"
    }
}
