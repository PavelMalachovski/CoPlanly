package com.coparently.app.data.documents

import android.content.Context
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.documents.VaultListing
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which vault the repository will observe (MON-23): only a family the signed-in account is one of
 * the two parents of, so a cached index a previous account left behind can never be listed.
 */
class FamilyDocumentRepositoryImplTest {

    private val index = mockk<FamilyDocumentIndex>()
    private val authService = mockk<FirebaseAuthService>()

    private val repository = FamilyDocumentRepositoryImpl(
        context = mockk<Context>(),
        index = index,
        authService = authService,
        stager = mockk(),
        storage = mockk(),
        cache = mockk()
    )

    private fun signedInAs(userId: String?) {
        val user = userId?.let { id -> mockk<FirebaseUser> { every { uid } returns id } }
        every { authService.getCurrentUser() } returns user
    }

    @Test
    fun `a family this account is in is observed through the index and its cache`() = runTest {
        signedInAs("alice")
        val listing = VaultListing(emptyList(), possiblyOutdated = true)
        every { index.observe("alice", FAMILY) } returns flowOf(listing)

        assertEquals(listOf<VaultListing?>(listing), repository.observe(FAMILY).toList())
    }

    @Test
    fun `a family this account is not in is never read, not even from the cache`() = runTest {
        signedInAs("carol")

        assertNull(repository.observe(FAMILY).toList().single())
        verify(exactly = 0) { index.observe(any(), any()) }
    }

    @Test
    fun `signed out, there is nothing to show`() = runTest {
        signedInAs(null)

        assertNull(repository.observe(FAMILY).toList().single())
        verify(exactly = 0) { index.observe(any(), any()) }
    }

    private companion object {
        const val FAMILY = "alice__bob"
    }
}
