package com.coparently.app.data.platform

import com.coparently.app.data.local.preferences.PreferenceBlobCodec
import com.coparently.app.data.local.preferences.PreferenceKeys

/**
 * Where a [SealedSecurePreferences] keeps its one text: on the iPhone, one Keychain item
 * (`KeychainTextStore`).
 */
interface SealedTextStore {

    /** The stored text, or null when there is none; throws when it is there but cannot be read. */
    fun read(): String?

    /** Replaces the stored text; throws when it cannot. */
    fun write(text: String)

    /** Removes the stored text, if any. */
    fun delete()
}

/**
 * [SecurePreferences] as one sealed text: the whole store, encoded by [PreferenceBlobCodec], held
 * in memory and written whole to [storage] on every edit (MON-24 phase 2, Task 6).
 *
 * The iPhone's store, over the Keychain. It follows the rules `EncryptedPreferences` follows on
 * Android (SEC-5, CLAUDE.md item 20), and `SecurePreferencesContractTest` holds the two to the same
 * answers:
 *  * every edit reaches [storage] before it returns, under one lock, so two edits are never written
 *    in the wrong order;
 *  * a text that cannot be read is deleted and the store starts empty (the parent signs in to
 *    Google Calendar again; nothing else here is not re-derivable);
 *  * opening writes at once, as a probe: storage that cannot be written leaves the store in memory
 *    for the life of the process — never anywhere in clear.
 *
 * The key names and every getter's default are `EncryptedPreferences`'s, so the two platforms read
 * one vocabulary.
 */
@Suppress("TooManyFunctions") // the interface's whole surface
class SealedSecurePreferences(private val storage: SealedTextStore) : SecurePreferences {

    private val monitor = newMonitor()
    private val values: MutableMap<String, Any> = mutableMapOf()
    private val persistent: Boolean

    init {
        values += readStored()
        persistent = writeStored(values)
        if (!persistent) logger.e(TAG, "No sealed storage available; keeping preferences in memory only")
    }

    override fun putAccessToken(token: String) = putString(KEY_ACCESS_TOKEN, token)

    override fun getAccessToken(): String? = getString(KEY_ACCESS_TOKEN)

    override fun putRefreshToken(token: String) = putString(KEY_REFRESH_TOKEN, token)

    override fun getRefreshToken(): String? = getString(KEY_REFRESH_TOKEN)

    override fun putTokenExpiry(expiryTimeMillis: Long) = edit { it[KEY_TOKEN_EXPIRY] = expiryTimeMillis }

    override fun getTokenExpiry(): Long? = (read(KEY_TOKEN_EXPIRY) as? Long ?: NONE).takeIf { it != NONE }

    override fun putCalendarId(calendarId: String) = putString(KEY_CALENDAR_ID, calendarId)

    override fun getCalendarId(): String? = getString(KEY_CALENDAR_ID, DEFAULT_CALENDAR_ID)

    override fun putGoogleIdToken(token: String) = putString(KEY_GOOGLE_ID_TOKEN, token)

    override fun getGoogleIdToken(): String? = getString(KEY_GOOGLE_ID_TOKEN)

    override fun putUserEmail(email: String) = putString(KEY_USER_EMAIL, email)

    override fun getUserEmail(): String? = getString(KEY_USER_EMAIL)

    override fun putDarkTheme(isDarkTheme: Boolean) = putBoolean(KEY_DARK_THEME, isDarkTheme)

    // As `EncryptedPreferences` reads it: a stored value that is not a boolean is "light".
    override fun getDarkTheme(): Boolean? =
        monitor.locked { if (KEY_DARK_THEME in values) values[KEY_DARK_THEME] as? Boolean ?: false else null }

    override fun clearDarkTheme() = remove(KEY_DARK_THEME)

    override fun putBoolean(key: String, value: Boolean) = edit { it[key] = value }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = read(key) as? Boolean ?: defaultValue

    override fun putString(key: String, value: String) = edit { it[key] = value }

    override fun getString(key: String, defaultValue: String?): String? = read(key) as? String ?: defaultValue

    override fun putEventDraft(familyId: String?, draftJson: String) = putString(eventDraftKey(familyId), draftJson)

    override fun getEventDraft(familyId: String?): String? = getString(eventDraftKey(familyId))

    override fun clearEventDraft(familyId: String?) = remove(eventDraftKey(familyId))

    override fun putChatDraft(conversationId: String, text: String) {
        val key = PreferenceKeys.CHAT_DRAFT_PREFIX + conversationId
        if (text.isEmpty()) remove(key) else putString(key, text)
    }

    override fun getChatDraft(conversationId: String): String =
        getString(PreferenceKeys.CHAT_DRAFT_PREFIX + conversationId).orEmpty()

    override fun putSplitRatioBasisPoints(basisPoints: Int) =
        edit { it[PreferenceKeys.SPLIT_RATIO_BASIS_POINTS] = basisPoints }

    override fun getSplitRatioBasisPoints(): Int? = readShare(PreferenceKeys.SPLIT_RATIO_BASIS_POINTS)

    override fun putSplitRatioSlot(slot: String?) =
        if (slot == null) remove(PreferenceKeys.SPLIT_RATIO_SLOT) else putString(PreferenceKeys.SPLIT_RATIO_SLOT, slot)

    override fun getSplitRatioSlot(): String? = getString(PreferenceKeys.SPLIT_RATIO_SLOT)

    override fun putFamilySplitRatioBasisPoints(familyId: String, basisPoints: Int) =
        edit { it[PreferenceKeys.SPLIT_RATIO_FAMILY_PREFIX + familyId] = basisPoints }

    override fun getFamilySplitRatioBasisPoints(familyId: String): Int? =
        readShare(PreferenceKeys.SPLIT_RATIO_FAMILY_PREFIX + familyId)

    override fun putDefaultCurrency(code: String) = putString(KEY_DEFAULT_CURRENCY, code)

    override fun getDefaultCurrency(): String? = getString(KEY_DEFAULT_CURRENCY)

    override fun keysWithPrefix(prefix: String): Set<String> =
        monitor.locked { values.keys.filterTo(mutableSetOf()) { it.startsWith(prefix) } }

    override fun remove(key: String) = edit { it.remove(key) }

    override fun removeWithPrefix(prefix: String) {
        val keys = keysWithPrefix(prefix)
        if (keys.isEmpty()) return
        edit { map -> keys.forEach { map.remove(it) } }
    }

    override fun removeGoogleTokens() = edit { map ->
        listOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_TOKEN_EXPIRY).forEach { map.remove(it) }
    }

    override fun removeGoogleCalendarAccount() = edit { map ->
        GOOGLE_CALENDAR_KEYS.forEach { map.remove(it) }
    }

    override fun clear() = edit { map ->
        // Only text survives, as on Android: every exempt entry is written as a string.
        val kept = map.filter { (key, value) -> value is String && PRESERVED_PREFIXES.any { key.startsWith(it) } }
        map.clear()
        map += kept
    }

    private fun read(key: String): Any? = monitor.locked { values[key] }

    private fun readShare(key: String): Int? = (read(key) as? Int ?: NONE_SHARE).takeIf { it >= 0 }

    /**
     * Applies [change] and writes the whole store, both under the lock. The memory keeps the change
     * when the write fails, as `EncryptedPreferences`' in-memory map does.
     */
    private fun edit(change: (MutableMap<String, Any>) -> Unit) {
        monitor.locked {
            change(values)
            if (persistent) writeStored(values)
        }
    }

    /** The stored entries; nothing when there are none or they cannot be read (the text is deleted). */
    private fun readStored(): Map<String, Any> {
        val text = try {
            storage.read()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            discard("Sealed storage unreadable; clearing it and starting again", e)
            return emptyMap()
        } ?: return emptyMap()
        return try {
            PreferenceBlobCodec.decode(text)
        } catch (e: IllegalArgumentException) {
            discard("Sealed text unreadable; clearing it and starting again", e)
            emptyMap()
        }
    }

    private fun discard(message: String, error: Exception) {
        logger.w(TAG, message, error)
        try {
            storage.delete()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(TAG, "Could not delete the unreadable sealed storage", e)
        }
    }

    /** Writes [snapshot]; false when it could not be. */
    private fun writeStored(snapshot: Map<String, Any>): Boolean = try {
        storage.write(PreferenceBlobCodec.encode(snapshot))
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(TAG, "Could not write the sealed storage", e)
        false
    }

    /** The draft key of [familyId]; the device-wide key for none, as on Android. */
    private fun eventDraftKey(familyId: String?): String =
        familyId?.takeIf { it.isNotBlank() }?.let { "${KEY_EVENT_DRAFT}_$it" } ?: KEY_EVENT_DRAFT

    private companion object {
        const val TAG = "SealedSecurePreferences"

        const val NONE = -1L
        const val NONE_SHARE = -1
        const val DEFAULT_CALENDAR_ID = "primary"

        // `EncryptedPreferences`' key names, so both platforms read one vocabulary.
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
        const val KEY_CALENDAR_ID = "calendar_id"
        const val KEY_GOOGLE_ID_TOKEN = "google_id_token"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_EVENT_DRAFT = "event_draft"
        const val KEY_DEFAULT_CURRENCY = "default_currency"

        val GOOGLE_CALENDAR_KEYS = listOf(
            KEY_ACCESS_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_TOKEN_EXPIRY,
            KEY_USER_EMAIL,
            KEY_CALENDAR_ID,
            KEY_GOOGLE_ID_TOKEN
        )

        /** The key families [clear] keeps; each prefix's own KDoc says why. */
        val PRESERVED_PREFIXES = listOf(
            PreferenceKeys.PARENT_SLOT_MARKER_PREFIX,
            PreferenceKeys.SCHOOL_CONNECTION_PREFIX
        )
    }
}

/** A lock a [SealedSecurePreferences] holds around every read and write. */
internal interface Monitor {
    /** Runs [block] holding the lock; re-entrant. */
    fun <T> locked(block: () -> T): T
}

/** `synchronized` on Android, `NSRecursiveLock` on the iPhone. */
internal expect fun newMonitor(): Monitor
