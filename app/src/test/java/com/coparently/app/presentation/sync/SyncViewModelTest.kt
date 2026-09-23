package com.coparently.app.presentation.sync

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncFailure
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStage
import com.coparently.app.data.sync.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Google Calendar half of Settings' sync group. How each result is *worded* is
 * `SyncStateTextTest`'s; this pins what the ViewModel does with the connection around it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val syncService = mockk<SyncService>(relaxed = true) {
        every { syncStatus } returns MutableStateFlow(SyncStatus.Idle)
    }
    private val calendarSyncRepository = mockk<CalendarSyncRepository>()
    private val credentials = mockk<CredentialManagerService>()
    private val preferences = mockk<EncryptedPreferences> {
        every { getUserEmail() } returns "olya@example.com"
    }

    private fun viewModel() = SyncViewModel(syncService, calendarSyncRepository, credentials, preferences)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an import without a Google connection says so and never reaches the repository`() = runTest(dispatcher) {
        every { credentials.isSignedIn() } returns false
        val vm = viewModel()

        vm.syncFromGoogle()
        advanceUntilIdle()

        assertEquals(
            GoogleCalendarSyncState.Error(SyncFailure.NOT_SIGNED_IN_GOOGLE.toUiText()),
            vm.syncState.value
        )
        coVerify(exactly = 0) { calendarSyncRepository.syncFromGoogle(any(), any()) }
    }

    @Test
    fun `turning sync on runs an import and ends on its result`() = runTest(dispatcher) {
        every { credentials.isSignedIn() } returns true
        val done = SyncResult.Success(
            synced = 3,
            from = LocalDate.of(2026, 9, 1),
            until = LocalDate.of(2027, 9, 1),
            truncated = false
        )
        coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } returns
            flowOf(SyncResult.Progress(SyncStage.STARTING), done)
        val vm = viewModel()

        vm.toggleSync(true)
        advanceUntilIdle()

        assertTrue(vm.isSyncEnabled.value)
        assertEquals(done.toSyncState(), vm.syncState.value)
    }

    @Test
    fun `a failed Google sign-out leaves the connection as it was`() = runTest(dispatcher) {
        every { credentials.isSignedIn() } returns true
        coEvery { credentials.signOut() } returns Pair(false, "offline")
        val vm = viewModel()

        val (success, _) = vm.signOut()

        assertFalse(success)
        assertTrue(vm.isSignedIn.value)
        assertEquals("olya@example.com", vm.userEmail.value)
    }

    @Test
    fun `a successful Google sign-out clears the connection`() = runTest(dispatcher) {
        every { credentials.isSignedIn() } returns true
        coEvery { credentials.signOut() } returns Pair(true, null)
        val vm = viewModel()
        vm.toggleSync(false)

        vm.signOut()

        assertFalse(vm.isSignedIn.value)
        assertFalse(vm.isSyncEnabled.value)
        assertNull(vm.userEmail.value)
        assertEquals(GoogleCalendarSyncState.Idle, vm.syncState.value)
    }

    @Test
    fun `a credential check that throws reads as not connected`() = runTest(dispatcher) {
        every { credentials.isSignedIn() } throws IllegalStateException("keystore")
        val vm = viewModel()

        assertFalse(vm.isSignedIn.value)
        assertNull(vm.userEmail.value)
    }
}
