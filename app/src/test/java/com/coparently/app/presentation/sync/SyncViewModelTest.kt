package com.coparently.app.presentation.sync

import android.content.Intent
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.remote.google.GoogleAccess
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.remote.google.GoogleCredentialsFixture
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncFailure
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStage
import com.coparently.app.data.sync.SyncStatus
import com.coparently.app.presentation.common.UiText
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Google Calendar half of Settings' sync group. How each result is *worded* is
 * `SyncStateTextTest`'s; this pins what the ViewModel does with the connection around it.
 *
 * Two kinds of fixture: mocks for what the ViewModel decides on its own, and a device's real
 * store and credential service ([GoogleCredentialsFixture]) for what has to hold across a launch —
 * the connection read from disk (#9: it read "not signed in" an hour after connecting) and the
 * "Enable sync" switch (#9: it read off after every restart).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val syncService = mockk<SyncService>(relaxed = true) {
        every { syncStatus } returns MutableStateFlow(SyncStatus.Idle)
    }
    private val calendarSyncRepository = mockk<CalendarSyncRepository>()
    private val credentials = mockk<CredentialManagerService>()
    private val preferences = mockk<EncryptedPreferences>(relaxUnitFun = true) {
        every { getUserEmail() } returns "olya@example.com"
        every { getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false) } returns false
    }
    private val google = GoogleCredentialsFixture()

    private fun viewModel() = SyncViewModel(syncService, calendarSyncRepository, credentials, preferences)

    /** The ViewModel a launch builds over [google]'s store. */
    private fun deviceViewModel() =
        SyncViewModel(syncService, calendarSyncRepository, google.service, google.preferences)

    /** The ViewModel the *next* launch builds, from what [google]'s store left on disk. */
    private fun relaunchedViewModel(): SyncViewModel {
        val store = google.reopened()
        val service = CredentialManagerService(google.context, store, google.oauth)
        return SyncViewModel(syncService, calendarSyncRepository, service, store)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        google.close()
    }

    @Test
    fun `an import without a Google connection says so and never reaches the repository`() = runTest(dispatcher) {
        every { credentials.connection() } returns GoogleCalendarConnection.NOT_CONNECTED
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
        google.connect()
        val done = SyncResult.Success(
            synced = 3,
            from = LocalDate.of(2026, 9, 1),
            until = LocalDate.of(2027, 9, 1),
            truncated = false
        )
        coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } returns
            flowOf(SyncResult.Progress(SyncStage.STARTING), done)
        val vm = deviceViewModel()

        vm.toggleSync(true)
        advanceUntilIdle()

        assertTrue(vm.isSyncEnabled.value)
        assertEquals(done.toSyncState(), vm.syncState.value)
    }

    @Test
    fun `a connection whose access token ran out reads as connected on launch`() = runTest(dispatcher) {
        // The refresh token renews it. Launch used to ask whether the *access* token had five
        // minutes left, so about an hour after connecting Settings said "not signed in".
        google.connect(expiresInMillis = -GoogleCredentialsFixture.HOUR)

        val vm = deviceViewModel()

        assertEquals(GoogleCalendarConnection.CONNECTED, vm.connection.value)
        assertEquals(GoogleCredentialsFixture.EMAIL, vm.userEmail.value)
    }

    @Test
    fun `an import on a run-out access token starts, rather than saying not connected`() = runTest(dispatcher) {
        // Renewing the token is the import's job (`CredentialProvider.withCredential`).
        google.connect(expiresInMillis = -GoogleCredentialsFixture.HOUR)
        val done = SyncResult.Success(
            synced = 1,
            from = LocalDate.of(2026, 9, 1),
            until = LocalDate.of(2027, 9, 1),
            truncated = false
        )
        coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } returns flowOf(done)
        val vm = deviceViewModel()

        vm.syncFromGoogle()
        advanceUntilIdle()

        assertEquals(done.toSyncState(), vm.syncState.value)
    }

    @Test
    fun `a connection Google ended reads as one to reconnect, and an import says so without trying`() =
        runTest(dispatcher) {
            // Its tokens were dropped when Google refused them; the address says it was connected.
            google.connect(accessToken = null, refreshToken = null)
            val vm = deviceViewModel()

            vm.syncFromGoogle()
            advanceUntilIdle()

            assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, vm.connection.value)
            assertEquals(GoogleCalendarSyncState.Error(SyncFailure.RECONNECT.toUiText()), vm.syncState.value)
            coVerify(exactly = 0) { calendarSyncRepository.syncFromGoogle(any(), any()) }
        }

    @Test
    fun `an import Google refuses for good turns the row to reconnect at once`() = runTest(dispatcher) {
        google.connect()
        coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } answers {
            // What `CredentialProvider.withCredential` does with a refusal for good, mid-import.
            google.service.markNeedsReconnect()
            flowOf(SyncResult.Error(SyncFailure.RECONNECT))
        }
        val vm = deviceViewModel()

        vm.syncFromGoogle()
        advanceUntilIdle()

        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, vm.connection.value)
        assertEquals(GoogleCalendarSyncState.Error(SyncFailure.RECONNECT.toUiText()), vm.syncState.value)
    }

    @Test
    fun `reconnecting runs Google's sign-in again and lands connected`() = runTest(dispatcher) {
        every { credentials.connection() } returns GoogleCalendarConnection.NEEDS_RECONNECT
        val intent = mockk<Intent>()
        val client = mockk<GoogleSignInClient> {
            every { signInIntent } returns intent
        }
        every { credentials.getGoogleSignInClient() } returns client
        val vm = viewModel()
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, vm.connection.value)

        // The Reconnect button's action: the same sign-in a first connection runs.
        assertSame(intent, vm.createGoogleSignInIntent())

        val account = mockk<GoogleSignInAccount> {
            every { displayName } returns "Olya"
            every { email } returns "olya@example.com"
        }
        val task = Tasks.forResult(account)
        coEvery { credentials.handleSignInResult(task) } returns Pair(account, null)
        coEvery { credentials.accessToken(any()) } returns GoogleAccess.Granted("access-new")
        every { credentials.connection() } returns GoogleCalendarConnection.CONNECTED

        val state = vm.handleSignInResult(task)

        assertEquals(GoogleCalendarConnection.CONNECTED, vm.connection.value)
        assertEquals(
            GoogleCalendarSyncState.Success(UiText.Res(R.string.sync_google_signed_in_as, listOf("Olya"))),
            state
        )
    }

    @Test
    fun `the sync switch is still on after a restart`() = runTest(dispatcher) {
        google.connect()
        coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } returns emptyFlow()

        deviceViewModel().toggleSync(true)
        advanceUntilIdle()

        val next = relaunchedViewModel()
        assertTrue(next.isSyncEnabled.value)
        assertEquals(GoogleCalendarConnection.CONNECTED, next.connection.value)
    }

    @Test
    fun `disconnecting Google Calendar keeps the switch, signing out of the app turns it off`() =
        runTest(dispatcher) {
            google.connect()
            coEvery { calendarSyncRepository.syncFromGoogle(any(), any()) } returns emptyFlow()
            val vm = deviceViewModel()
            vm.toggleSync(true)
            advanceUntilIdle()

            vm.disconnectGoogleCalendar()

            assertEquals(GoogleCalendarConnection.NOT_CONNECTED, vm.connection.value)
            assertNull(vm.userEmail.value)
            assertTrue(vm.isSyncEnabled.value)
            assertTrue(relaunchedViewModel().isSyncEnabled.value, "the switch outlives the disconnect")

            vm.signOut()

            assertFalse(vm.isSyncEnabled.value)
            assertFalse(relaunchedViewModel().isSyncEnabled.value, "an app sign-out clears it with everything else")
        }

    @Test
    fun `opening Settings again reads what the store holds now`() = runTest(dispatcher) {
        // The ViewModel lives as long as the activity; an account deleted or switched meanwhile
        // empties the store behind it.
        google.connect()
        val vm = deviceViewModel()

        google.preferences.removeGoogleCalendarAccount()
        vm.refreshConnection()

        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, vm.connection.value)
        assertNull(vm.userEmail.value)
    }

    @Test
    fun `a failed Google sign-out leaves the connection as it was`() = runTest(dispatcher) {
        every { credentials.connection() } returns GoogleCalendarConnection.CONNECTED
        coEvery { credentials.signOut() } returns Pair(false, "offline")
        val vm = viewModel()

        val (success, _) = vm.signOut()

        assertFalse(success)
        assertEquals(GoogleCalendarConnection.CONNECTED, vm.connection.value)
        assertEquals("olya@example.com", vm.userEmail.value)
    }

    @Test
    fun `a successful Google sign-out clears the connection`() = runTest(dispatcher) {
        every { credentials.connection() } returns GoogleCalendarConnection.CONNECTED
        coEvery { credentials.signOut() } returns Pair(true, null)
        val vm = viewModel()
        vm.toggleSync(false)

        vm.signOut()

        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, vm.connection.value)
        assertFalse(vm.isSyncEnabled.value)
        assertNull(vm.userEmail.value)
        assertEquals(GoogleCalendarSyncState.Idle, vm.syncState.value)
    }

    @Test
    fun `a credential check that throws reads as not connected`() = runTest(dispatcher) {
        every { credentials.connection() } throws IllegalStateException("keystore")
        val vm = viewModel()

        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, vm.connection.value)
        assertNull(vm.userEmail.value)
    }
}
