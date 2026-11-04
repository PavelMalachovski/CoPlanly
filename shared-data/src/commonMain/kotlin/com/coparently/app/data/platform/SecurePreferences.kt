package com.coparently.app.data.platform

/**
 * The app's small sealed key-value store (MON-24 phase 2, Task 6): Google's tokens, the parent's
 * settings, drafts, sync markers and the school connections.
 *
 * Two implementations, held to one contract (`SecurePreferencesContractTest`):
 *  * Android: `EncryptedPreferences`, the SEC-5 store — one file under `noBackupFilesDir`, sealed
 *    with a Keystore key (CLAUDE.md, item 20). It stays in the app for now; nothing about it moved.
 *  * iPhone: [SealedSecurePreferences] over one Keychain item (`KeychainTextStore`).
 *
 * Every write reaches the sealed storage before it returns. **Never open two instances over one
 * storage**: each keeps its own copy of the whole store, and each write replaces the other's (item
 * 20). The graph holds one.
 *
 * [clear] keeps two families of keys — the parent-slot markers and the school connections — for
 * the reasons their prefixes' KDoc in `PreferenceKeys` gives (item 35).
 */
@Suppress("TooManyFunctions") // the store's whole surface, one accessor per stored fact
interface SecurePreferences {

    /** Stores the Google access token. */
    fun putAccessToken(token: String)

    /** The Google access token, or null. */
    fun getAccessToken(): String?

    /** Stores the Google refresh token. */
    fun putRefreshToken(token: String)

    /** The Google refresh token, or null. */
    fun getRefreshToken(): String?

    /** Stores when the access token expires, in epoch millis. */
    fun putTokenExpiry(expiryTimeMillis: Long)

    /** When the access token expires, or null when unknown. */
    fun getTokenExpiry(): Long?

    /** Stores the Google calendar id. */
    fun putCalendarId(calendarId: String)

    /** The Google calendar id, `"primary"` when none was stored. */
    fun getCalendarId(): String?

    /** Stores the Google ID token. */
    fun putGoogleIdToken(token: String)

    /** The Google ID token, or null. */
    fun getGoogleIdToken(): String?

    /** Stores the Google account's address. */
    fun putUserEmail(email: String)

    /** The Google account's address, or null. */
    fun getUserEmail(): String?

    /** Stores the theme choice. */
    fun putDarkTheme(isDarkTheme: Boolean)

    /** The theme choice, or null to follow the system. */
    fun getDarkTheme(): Boolean?

    /** Forgets the theme choice: the system's applies again. */
    fun clearDarkTheme()

    /** Stores [value] under [key]. */
    fun putBoolean(key: String, value: Boolean)

    /** The boolean under [key], or [defaultValue]. */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /** Stores [value] under [key]. */
    fun putString(key: String, value: String)

    /** The text under [key], or [defaultValue]. */
    fun getString(key: String, defaultValue: String? = null): String?

    /** Stores the new-event draft of [familyId]'s form; one per family, never shared. */
    fun putEventDraft(familyId: String?, draftJson: String)

    /** [familyId]'s new-event draft, never another family's. */
    fun getEventDraft(familyId: String?): String?

    /** Forgets [familyId]'s new-event draft. */
    fun clearEventDraft(familyId: String?)

    /** Stores the unsent composer text of [conversationId]; an empty [text] removes it. */
    fun putChatDraft(conversationId: String, text: String)

    /** The unsent composer text of [conversationId], or an empty string. */
    fun getChatDraft(conversationId: String): String

    /** Stores the agreed split as slot 1's share in basis points, `0..10000`. */
    fun putSplitRatioBasisPoints(basisPoints: Int)

    /** The agreed split, or null when none was ever agreed. */
    fun getSplitRatioBasisPoints(): Int?

    /** Stores which slot the cached share belongs to; null forgets it. */
    fun putSplitRatioSlot(slot: String?)

    /** The slot the cached share belongs to, or null. */
    fun getSplitRatioSlot(): String?

    /** Stores [familyId]'s agreed split, `0..10000`. */
    fun putFamilySplitRatioBasisPoints(familyId: String, basisPoints: Int)

    /** [familyId]'s agreed split, or null — never another family's. */
    fun getFamilySplitRatioBasisPoints(familyId: String): Int?

    /** Stores the default currency, an ISO 4217 code. */
    fun putDefaultCurrency(code: String)

    /** The default currency, or null when none was ever resolved. */
    fun getDefaultCurrency(): String?

    /** Every stored key that starts with [prefix]. */
    fun keysWithPrefix(prefix: String): Set<String>

    /** Removes [key]. */
    fun remove(key: String)

    /** Removes every key that starts with [prefix], in one write. */
    fun removeWithPrefix(prefix: String)

    /** Forgets Google's tokens and their expiry, and keeps the address. */
    fun removeGoogleTokens()

    /** Forgets everything Google Calendar keeps here, and nothing else, in one write. */
    fun removeGoogleCalendarAccount()

    /** Forgets everything but the parent-slot markers and the school connections. */
    fun clear()
}
