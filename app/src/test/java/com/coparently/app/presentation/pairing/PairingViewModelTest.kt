package com.coparently.app.presentation.pairing

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.remote.firebase.CoParentPairingService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

/**
 * Unit tests for PairingViewModel.
 * Tests pairing invitation flows and validation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var pairingService: CoParentPairingService
    private lateinit var firebaseAuthService: FirebaseAuthService
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: PairingViewModel

    private val mockUser = mockk<FirebaseUser> {
        every { uid } returns "test_uid"
        every { email } returns "test@example.com"
    }

    private val testUser = User(
        firebaseUid = "test_uid",
        email = "test@example.com",
        name = "Test User",
        partnerId = null
    )

    private val testUserWithPartner = testUser.copy(partnerId = "partner_uid")

    private val testPartnerInfo = mapOf(
        "email" to "partner@example.com",
        "name" to "Partner"
    )

    private val testInvitations = listOf(
        mapOf(
            "id" to "inv1",
            "fromEmail" to "sender@example.com",
            "status" to "pending"
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        pairingService = mockk()
        firebaseAuthService = mockk()
        userRepository = mockk()

        every { firebaseAuthService.getCurrentUser() } returns mockUser
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { pairingService.getPartnerInfo(any()) } returns null
        coEvery { pairingService.getPendingInvitations(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `init loads pending invitations`() = runTest {
        // Given
        coEvery { pairingService.getPendingInvitations(any()) } returns Result.success(testInvitations)

        // When
        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testInvitations, state.pendingInvitations)
        }

        coVerify { pairingService.getPendingInvitations("test@example.com") }
    }

    @Test
    fun `init loads partner info when user has partner`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUserWithPartner
        coEvery { pairingService.getPartnerInfo("partner_uid") } returns testPartnerInfo

        // When
        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("partner@example.com", state.partnerEmail)
        }

        coVerify { pairingService.getPartnerInfo("partner_uid") }
    }

    @Test
    fun `updateInvitationEmail updates email and clears errors`() = runTest {
        // Given
        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("invite@example.com")

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("invite@example.com", state.invitationEmail)
            assertNull(state.errorMessage)
            assertNull(state.emailError)
        }
    }

    @Test
    fun `sendInvitation with invalid email shows validation error`() = runTest {
        // Given
        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("invalid-email")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.emailError)
            assertTrue(state.emailError!!.contains("Invalid email"))
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `sendInvitation to self shows error`() = runTest {
        // Given
        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("test@example.com")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("You cannot invite yourself", state.emailError)
            assertFalse(state.isLoading)
        }
        assertFalse(callbackInvoked)
    }

    @Test
    fun `sendInvitation with valid email succeeds`() = runTest {
        // Given
        coEvery {
            pairingService.sendInvitation(any(), any(), any(), any())
        } returns Result.success(Unit)

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("partner@example.com")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertTrue(callbackInvoked)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("", state.invitationEmail) // Cleared after success
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }

        coVerify {
            pairingService.sendInvitation(
                "test_uid",
                "test@example.com",
                "Test User",
                "partner@example.com"
            )
        }
    }

    @Test
    fun `sendInvitation handles service error`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery {
            pairingService.sendInvitation(any(), any(), any(), any())
        } returns Result.failure(Exception(errorMessage))

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("partner@example.com")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertFalse(callbackInvoked)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(errorMessage, state.errorMessage)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `sendInvitation when user not authenticated`() = runTest {
        // Given
        every { firebaseAuthService.getCurrentUser() } returns null

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("partner@example.com")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertFalse(callbackInvoked)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("User not authenticated. Please sign in.", state.errorMessage)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `sendInvitation when user data not found`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns null

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("partner@example.com")
        var callbackInvoked = false
        viewModel.sendInvitation { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Then
        assertFalse(callbackInvoked)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("User data not found. Please try again.", state.errorMessage)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `sendInvitation shows loading state`() = runTest {
        // Given
        coEvery {
            pairingService.sendInvitation(any(), any(), any(), any())
        } coAnswers {
            kotlinx.coroutines.delay(100)
            Result.success(Unit)
        }

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateInvitationEmail("partner@example.com")
        viewModel.sendInvitation { }

        // Then - should be loading
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `acceptInvitation succeeds`() = runTest {
        // Given
        val invitationId = "inv1"
        coEvery { pairingService.acceptInvitation(invitationId, "test_uid") } returns Result.success(Unit)
        coEvery { pairingService.getPartnerInfo(any()) } returns testPartnerInfo

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.acceptInvitation(invitationId)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { pairingService.acceptInvitation(invitationId, "test_uid") }
        // Should reload pairing info and invitations
        coVerify(atLeast = 2) { userRepository.getCurrentUser() }
        coVerify(atLeast = 2) { pairingService.getPendingInvitations(any()) }
    }

    @Test
    fun `acceptInvitation when user not authenticated`() = runTest {
        // Given
        every { firebaseAuthService.getCurrentUser() } returns null
        val invitationId = "inv1"

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.acceptInvitation(invitationId)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { pairingService.acceptInvitation(any(), any()) }
    }

    @Test
    fun `rejectInvitation succeeds`() = runTest {
        // Given
        val invitationId = "inv1"
        coEvery { pairingService.rejectInvitation(invitationId) } returns Result.success(Unit)
        coEvery { pairingService.getPendingInvitations(any()) } returns Result.success(emptyList())

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.rejectInvitation(invitationId)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { pairingService.rejectInvitation(invitationId) }
        // Should reload invitations
        coVerify(atLeast = 2) { pairingService.getPendingInvitations(any()) }
    }

    @Test
    fun `removePairing succeeds`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUserWithPartner
        coEvery { pairingService.getPartnerInfo(any()) } returns testPartnerInfo
        coEvery { pairingService.removePartnership("test_uid", "partner_uid") } returns Result.success(Unit)

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.removePairing()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { pairingService.removePartnership("test_uid", "partner_uid") }
        // Should reload pairing info
        coVerify(atLeast = 2) { userRepository.getCurrentUser() }
    }

    @Test
    fun `removePairing when user not authenticated`() = runTest {
        // Given
        every { firebaseAuthService.getCurrentUser() } returns null

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.removePairing()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { pairingService.removePartnership(any(), any()) }
    }

    @Test
    fun `removePairing when user has no partner`() = runTest {
        // Given - testUser has no partnerId

        viewModel = PairingViewModel(pairingService, firebaseAuthService, userRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.removePairing()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { pairingService.removePartnership(any(), any()) }
    }
}

