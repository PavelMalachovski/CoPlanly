package com.coparently.app.data.session

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SignedInAccountSource].
 *
 * This is what answers "which account is this phone signed in as" on the pairing screen.
 * The question is not cosmetic: both parents' phones render the same invite-code layout,
 * and the owner could not tell his two devices apart from it.
 *
 * The properties worth pinning are the ones that make the answer trustworthy rather than
 * merely present: it combines the auth session with the stored row instead of picking one
 * (an email/password account has no name or photo in the session at all), it prefers the
 * fresher of the two, and it never claims an account while signed out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignedInAccountSourceTest {

    private val authService: SignedInUser = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)

    @Test
    fun `reports the google display name, email and photo`() = runTest {
        signedIn(displayName = "Alice Novak", email = "alice@example.com", photoUrl = PHOTO)
        storedRow(null)

        val account = source().observe().first()

        assertEquals(UID, account?.id)
        assertEquals("Alice Novak", account?.name)
        assertEquals("alice@example.com", account?.email)
        assertEquals(PHOTO, account?.photoUrl)
    }

    @Test
    fun `falls back to the stored profile when the session carries no name or photo`() = runTest {
        // Every email/password session: Firebase Auth has the email and nothing else, so
        // without the stored row this block would read "alice" with a bare initial.
        signedIn(displayName = null, email = "alice@example.com", photoUrl = null)
        storedRow(row(name = "Alice Novak", photoUrl = PHOTO))

        val account = source().observe().first()

        assertEquals("Alice Novak", account?.name)
        assertEquals(PHOTO, account?.photoUrl)
    }

    @Test
    fun `prefers the session photo over a stale stored one`() = runTest {
        signedIn(displayName = "Alice Novak", email = "alice@example.com", photoUrl = NEW_PHOTO)
        storedRow(row(name = "Alice Novak", photoUrl = PHOTO))

        assertEquals(NEW_PHOTO, source().observe().first()?.photoUrl)
    }

    @Test
    fun `falls back to the email local part when nothing carries a name`() = runTest {
        signedIn(displayName = null, email = "alice@example.com", photoUrl = null)
        storedRow(row(name = "", photoUrl = null))

        val account = source().observe().first()

        assertEquals("alice", account?.name)
        assertNull(account?.photoUrl)
    }

    @Test
    fun `recovers name and photo from providerData when the session carries neither`() = runTest {
        // The same Samsung shape UserRepositoryImpl.ensureProfile repairs: a genuine Google
        // session whose account record predates linking the provider, top-level fields
        // empty, google.com entry in providerData carrying all three.
        val provider = googleProviderInfo(displayName = "Alice Novak", email = "alice@example.com", photoUrl = PHOTO)
        signedIn(displayName = null, email = null, photoUrl = null, providers = listOf(provider))
        storedRow(null)

        val account = source().observe().first()

        assertEquals("Alice Novak", account?.name)
        assertEquals("alice@example.com", account?.email)
        assertEquals(PHOTO, account?.photoUrl)
    }

    @Test
    fun `reports nothing while signed out`() = runTest {
        every { authService.observeCurrentUid() } returns flowOf(null)

        assertNull(source().observe().first())
    }

    @Test
    fun `reports nothing for an account with no identity at all`() = runTest {
        // An anonymous session has no email and no name; a "signed in as" block with two
        // blank lines is worse than no block, so the screen is told there is nothing.
        signedIn(displayName = null, email = null, photoUrl = null)
        storedRow(null)

        assertNull(source().observe().first())
    }

    @Test
    fun `re-emits when the stored profile is filled in`() = runTest {
        // `SessionProfileSynchronizer` repairs the row shortly after sign-in; the block has
        // to fill itself in when that lands rather than stay on the first snapshot.
        signedIn(displayName = null, email = "alice@example.com", photoUrl = null)
        every { userDao.observeUserById(UID) } returns
            flowOf(null, row(name = "Alice Novak", photoUrl = PHOTO))

        val names = source().observe().toList().map { it?.name }

        assertEquals(listOf("alice", "Alice Novak"), names)
    }

    @Test
    fun `says nothing for a report another account has already replaced`() = runTest {
        // The row arrives after a switch: the account signed in now is not the one reported.
        signedIn(displayName = "Alice Novak", email = "alice@example.com", photoUrl = null)
        every { authService.getCurrentAccount() } returns SignedInAccount(
            uid = "user-b",
            displayName = "Bob",
            email = "bob@example.com",
            photoUrl = null,
            isAnonymous = false,
            providers = emptyList()
        )
        storedRow(row(name = "Alice Novak", photoUrl = null))

        assertNull(source().observe().first())
    }

    private fun source() = SignedInAccountSource(authService, userDao)

    private fun signedIn(
        displayName: String?,
        email: String?,
        photoUrl: String?,
        providers: List<ProfileIdentity.ProviderIdentity> = emptyList()
    ) {
        every { authService.observeCurrentUid() } returns flowOf(UID)
        every { authService.getCurrentAccount() } returns SignedInAccount(
            uid = UID,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl,
            isAnonymous = false,
            providers = providers
        )
    }

    /** A `providerData` entry as `google.com` reports it — see [ProfileIdentity.bestProvider]. */
    private fun googleProviderInfo(
        displayName: String? = null,
        email: String? = null,
        photoUrl: String? = null
    ) = ProfileIdentity.ProviderIdentity(
        providerId = "google.com",
        displayName = displayName,
        email = email,
        photoUrl = photoUrl
    )

    private fun storedRow(entity: UserEntity?) {
        every { userDao.observeUserById(UID) } returns flowOf(entity)
    }

    private fun row(name: String, photoUrl: String?) = UserEntity(
        id = UID,
        email = "alice@example.com",
        name = name,
        role = "mom",
        colorCode = "#FF4081",
        profilePhotoUrl = photoUrl
    )

    private companion object {
        const val UID = "user-a"
        const val PHOTO = "https://lh3.googleusercontent.com/a/alice"
        const val NEW_PHOTO = "https://lh3.googleusercontent.com/a/alice-v2"
    }
}
