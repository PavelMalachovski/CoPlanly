package com.coparently.app.presentation.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStatus
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SyncViewModel.
 * Tests Google Calendar sync and Firestore sync operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var syncService: SyncService
    private lateinit var calendarSyncRepository: CalendarSyncRepository
    private lateinit var credentialManagerService: CredentialManagerService
    private lateinit var encryptedPreferences: EncryptedPreferences
    private lateinit var viewModel: SyncViewModel

    private val testEmail = "test@example.com"
    private val mockCredential = mockk<GoogleIdTokenCredential> {
        every { id } returns testEmail
        every { displayName } returns "Test User"
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        syncService = mockk()
        calendarSyncRepository = mockk()
        credentialManagerService = mockk()
        encryptedPreferences = mockk()

        // Default mock behaviors
        every { credentialManagerService.isSignedIn() } returns false
        every { encryptedPreferences.getUserEmail() } returns null
        every { syncService.syncStatus } returns MutableStateFlow(SyncStatus.Idle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `init checks sign-in status`() = runTest {
        // Given
        every { credentialManagerService.isSignedIn() } returns true
        every { encryptedPreferences.getUserEmail() } returns testEmail

        // When
        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // Then
        viewModel.isSignedIn.test {
            val isSignedIn = awaitItem()
            assertTrue(isSignedIn)
        }

        viewModel.userEmail.test {
            val email = awaitItem()
            assertEquals(testEmail, email)
        }

        verify { credentialManagerService.isSignedIn() }
        verify { encryptedPreferences.getUserEmail() }
    }

    @Test
    fun `init when not signed in`() = runTest {
        // Given - default mocks return false/null

        // When
        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // Then
        viewModel.isSignedIn.test {
            val isSignedIn = awaitItem()
            assertFalse(isSignedIn)
        }

        viewModel.userEmail.test {
            val email = awaitItem()
            assertNull(email)
        }
    }

    @Test
    fun `signIn succeeds with credential and token`() = runTest {
        // Given
        coEvery {
            credentialManagerService.getGoogleIdCredential(false)
        } returns Pair(mockCredential, null)
        coEvery {
            credentialManagerService.getAccessToken(testEmail)
        } returns Pair("test_token", null)
        every { encryptedPreferences.putUserEmail(testEmail) } just Runs

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        val result = viewModel.signIn()
        testScheduler.advanceUntilIdle()

        // Then
        assertIs<GoogleCalendarSyncState.Success>(result)
        assertEquals("Signed in successfully as Test User", result.message)

        viewModel.isSignedIn.test {
            assertTrue(awaitItem())
        }

        viewModel.userEmail.test {
            assertEquals(testEmail, awaitItem())
        }

        coVerify { credentialManagerService.getGoogleIdCredential(false) }
        coVerify { credentialManagerService.getAccessToken(testEmail) }
        verify { encryptedPreferences.putUserEmail(testEmail) }
    }

    @Test
    fun `signIn fails when credential is null`() = runTest {
        // Given
        val errorMessage = "User cancelled"
        coEvery {
            credentialManagerService.getGoogleIdCredential(false)
        } returns Pair(null, errorMessage)

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        val result = viewModel.signIn()
        testScheduler.advanceUntilIdle()

        // Then
        assertIs<GoogleCalendarSyncState.Error>(result)
        assertEquals(errorMessage, result.message)

        viewModel.isSignedIn.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `signIn fails when token is null`() = runTest {
        // Given
        val tokenError = "No calendar access"
        coEvery {
            credentialManagerService.getGoogleIdCredential(false)
        } returns Pair(mockCredential, null)
        coEvery {
            credentialManagerService.getAccessToken(testEmail)
        } returns Pair(null, tokenError)
        every { encryptedPreferences.putUserEmail(testEmail) } just Runs

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        val result = viewModel.signIn()
        testScheduler.advanceUntilIdle()

        // Then
        assertIs<GoogleCalendarSyncState.Error>(result)
        assertEquals(tokenError, result.message)

        viewModel.isSignedIn.test {
            assertTrue(awaitItem()) // Still signed in, just no token
        }
    }

    @Test
    fun `retrySignIn succeeds`() = runTest {
        // Given
        coEvery {
            credentialManagerService.getGoogleIdCredential(true)
        } returns Pair(mockCredential, null)
        coEvery {
            credentialManagerService.getAccessToken(testEmail)
        } returns Pair("test_token", null)
        every { encryptedPreferences.putUserEmail(testEmail) } just Runs

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        val result = viewModel.retrySignIn()
        testScheduler.advanceUntilIdle()

        // Then
        assertIs<GoogleCalendarSyncState.Success>(result)

        viewModel.isSignedIn.test {
            assertTrue(awaitItem())
        }

        // Verify filterByAuthorizedAccounts = true
        coVerify { credentialManagerService.getGoogleIdCredential(true) }
    }

    @Test
    fun `toggleSync enables sync and triggers syncFromGoogle`() = runTest {
        // Given
        every { credentialManagerService.isSignedIn() } returns true
        every { encryptedPreferences.getUserEmail() } returns testEmail
        coEvery { credentialManagerService.refreshAccessToken(testEmail) } just Runs
        coEvery { calendarSyncRepository.syncFromGoogle() } returns flowOf(
            SyncResult.Success("Synced successfully")
        )

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.toggleSync(true)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.isSyncEnabled.test {
            assertTrue(awaitItem())
        }

        viewModel.syncState.test {
            val state = awaitItem()
            assertIs<GoogleCalendarSyncState.Success>(state)
        }

        coVerify { calendarSyncRepository.syncFromGoogle() }
    }

    @Test
    fun `toggleSync disables sync without triggering syncFromGoogle`() = runTest {
        // Given
        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.toggleSync(false)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.isSyncEnabled.test {
            assertFalse(awaitItem())
        }

        coVerify(exactly = 0) { calendarSyncRepository.syncFromGoogle() }
    }

    @Test
    fun `syncFromGoogle when not signed in shows error`() = runTest {
        // Given
        every { credentialManagerService.isSignedIn() } returns false

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.syncFromGoogle()

        // Then
        viewModel.syncState.test {
            val state = awaitItem()
            assertIs<GoogleCalendarSyncState.Error>(state)
            assertEquals("Not signed in to Google", state.message)
        }

        coVerify(exactly = 0) { calendarSyncRepository.syncFromGoogle() }
    }

    @Test
    fun `syncFromGoogle handles progress updates`() = runTest {
        // Given
        every { credentialManagerService.isSignedIn() } returns true
        every { encryptedPreferences.getUserEmail() } returns testEmail
        coEvery { credentialManagerService.refreshAccessToken(testEmail) } just Runs
        coEvery { calendarSyncRepository.syncFromGoogle() } returns flowOf(
            SyncResult.Progress("Loading events..."),
            SyncResult.Success("Synced 5 events")
        )

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.syncFromGoogle()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.syncState.test {
            val state = awaitItem()
            assertIs<GoogleCalendarSyncState.Success>(state)
        }

        coVerify { credentialManagerService.refreshAccessToken(testEmail) }
        coVerify { calendarSyncRepository.syncFromGoogle() }
    }

    @Test
    fun `syncFromGoogle handles sync error`() = runTest {
        // Given
        val errorMessage = "Network error"
        every { credentialManagerService.isSignedIn() } returns true
        every { encryptedPreferences.getUserEmail() } returns testEmail
        coEvery { credentialManagerService.refreshAccessToken(testEmail) } just Runs
        coEvery { calendarSyncRepository.syncFromGoogle() } returns flowOf(
            SyncResult.Error(errorMessage)
        )

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.syncFromGoogle()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.syncState.test {
            val state = awaitItem()
            assertIs<GoogleCalendarSyncState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `signOut clears all state`() = runTest {
        // Given
        every { credentialManagerService.isSignedIn() } returns true
        every { encryptedPreferences.getUserEmail() } returns testEmail
        every { credentialManagerService.signOut() } just Runs

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.signOut()

        // Then
        viewModel.isSignedIn.test {
            assertFalse(awaitItem())
        }

        viewModel.isSyncEnabled.test {
            assertFalse(awaitItem())
        }

        viewModel.syncState.test {
            val state = awaitItem()
            assertIs<GoogleCalendarSyncState.Idle>(state)
        }

        viewModel.userEmail.test {
            assertNull(awaitItem())
        }

        verify { credentialManagerService.signOut() }
    }

    @Test
    fun `performFirestoreSync triggers full sync`() = runTest {
        // Given
        coEvery { syncService.performFullSync() } just Runs

        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // When
        viewModel.performFirestoreSync()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { syncService.performFullSync() }
    }

    @Test
    fun `firestoreSyncStatus reflects syncService status`() = runTest {
        // Given
        val syncStatusFlow = MutableStateFlow<SyncStatus>(SyncStatus.Syncing)
        every { syncService.syncStatus } returns syncStatusFlow

        // When
        viewModel = SyncViewModel(
            syncService,
            calendarSyncRepository,
            credentialManagerService,
            encryptedPreferences
        )

        // Then
        viewModel.firestoreSyncStatus.test {
            val status = awaitItem()
            assertIs<SyncStatus.Syncing>(status)

            // Change status
            syncStatusFlow.value = SyncStatus.Success
            val newStatus = awaitItem()
            assertIs<SyncStatus.Success>(newStatus)
        }
    }
}

