package com.coparently.app.data.remote.google

import android.content.Context
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.InMemorySharedPreferences
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.coparently.app.data.remote.firebase.GoogleTokens
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

/**
 * One device's Google Calendar credential, for JVM tests: the real [EncryptedPreferences] over a
 * temporary directory (sealed with a stand-in cipher, as `SchoolTestSupport` does), the real
 * [CredentialManagerService] over it, and [oauth] standing in for the token callables.
 *
 * The context names no OAuth client — `YOUR_WEB_CLIENT_ID` is the placeholder the service refuses
 * — so the Google sign-in client resolves to null and nothing here reaches Play services: a
 * disconnect's revocation is skipped, as on a build with no OAuth client configured.
 */
internal class GoogleCredentialsFixture {

    private val dir: File = Files.createTempDirectory("google-credentials").toFile()

    /** The application context the store and the service are built on. */
    val context: Context = mockk(relaxed = true)

    /** The token callables; each test stubs `refreshAccessToken`/`exchangeAuthCode` as it needs. */
    val oauth: GoogleOAuthFunctions = mockk()

    init {
        every { context.noBackupFilesDir } returns dir
        every { context.getSharedPreferences(any(), any()) } returns InMemorySharedPreferences()
        every { context.getString(any<Int>()) } returns UNCONFIGURED_CLIENT_ID
    }

    /** The store, as this device holds it. */
    val preferences: EncryptedPreferences = EncryptedPreferences(context, ReversingCipher)

    /** The service under test, over [preferences]. */
    val service: CredentialManagerService = CredentialManagerService(context, preferences, oauth)

    /** The store as the next launch reads it back from disk. */
    fun reopened(): EncryptedPreferences = EncryptedPreferences(context, ReversingCipher)

    /**
     * Stores a connection as an exchange would: [accessToken] running out [expiresInMillis] from now
     * (negative for one that already has), [refreshToken] and [email]; null leaves one out.
     */
    fun connect(
        accessToken: String? = ACCESS,
        expiresInMillis: Long = HOUR,
        refreshToken: String? = REFRESH,
        email: String? = EMAIL
    ) {
        accessToken?.let {
            preferences.putAccessToken(it)
            preferences.putTokenExpiry(System.currentTimeMillis() + expiresInMillis)
        }
        refreshToken?.let { preferences.putRefreshToken(it) }
        email?.let { preferences.putUserEmail(it) }
    }

    /** What the refresh callable answers for a renewal that worked. */
    fun renewed(accessToken: String = FRESH) =
        Result.success(GoogleTokens(accessToken = accessToken, refreshToken = "", expiresInSeconds = 3599))

    /** Deletes the temporary directory. */
    fun close() {
        dir.deleteRecursively()
    }

    /** Not encryption — just enough of a transform that the sealed file is not the plain map. */
    private object ReversingCipher : EncryptedPreferences.StoreCipher {
        override fun seal(plain: String): String = plain.reversed()
        override fun open(sealed: String): String = sealed.reversed()
    }

    companion object {
        const val ACCESS = "access-1"
        const val REFRESH = "refresh-1"
        const val FRESH = "access-fresh"
        const val EMAIL = "alice@example.test"
        const val HOUR = 60 * 60 * 1000L

        /** The value `CredentialManagerService` reads as "no OAuth client configured". */
        const val UNCONFIGURED_CLIENT_ID = "YOUR_WEB_CLIENT_ID"
    }
}
