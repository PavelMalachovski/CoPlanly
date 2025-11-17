package com.coparently.app.presentation.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.ErrorType
import com.coparently.app.presentation.common.UiState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for SettingsViewModel.
 * Tests settings management, notification toggles, and theme preferences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fcmService: FcmService
    private lateinit var userRepository: UserRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    private val testUser = User(
        firebaseUid = "test_uid",
        email = "test@example.com",
        name = "Test User",
        partnerId = "partner_uid"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fcmService = mockk()
        userRepository = mockk()
        preferencesRepository = mockk()

        // Default mock behaviors
        coEvery { preferencesRepository.getDarkThemeFlow() } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `loadSettings loads successfully with user and FCM token`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_fcm_token"

        // When
        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(true, state.notificationsEnabled)
            assertEquals(testUser.email, state.userEmail)
            assertEquals(testUser.name, state.userName)
            assertEquals(testUser.partnerId, state.partnerId)
            assertEquals(false, state.isLoading)
        }

        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Success<Unit>>(state)
        }

        coVerify { userRepository.getCurrentUser() }
        coVerify { fcmService.getCurrentToken() }
    }

    @Test
    fun `loadSettings handles missing FCM token`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns null

        // When
        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(false, state.notificationsEnabled)
            assertEquals(testUser.email, state.userEmail)
        }
    }

    @Test
    fun `loadSettings handles error from repository`() = runTest {
        // Given
        val exception = IOException("Network error")
        coEvery { userRepository.getCurrentUser() } throws exception
        coEvery { fcmService.getCurrentToken() } returns "test_token"

        // When
        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
            assertEquals(ErrorType.NETWORK, state.error.type)
            assertNotNull(state.error.retry)
        }

        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun `toggleNotifications enables notifications successfully`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { fcmService.updateUserToken(any()) } returns Result.success(Unit)

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleNotifications(true)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(true, state.notificationsEnabled)
            assertEquals("Notifications enabled", state.successMessage)
        }

        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Success<Unit>>(state)
            assertEquals("Notifications enabled successfully", state.message)
        }

        coVerify { fcmService.getCurrentToken() }
        coVerify { fcmService.updateUserToken("test_token") }
    }

    @Test
    fun `toggleNotifications disables notifications successfully`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleNotifications(false)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(false, state.notificationsEnabled)
            assertEquals("Notifications disabled", state.successMessage)
        }

        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Success<Unit>>(state)
        }
    }

    @Test
    fun `toggleNotifications handles network error`() = runTest {
        // Given
        val networkException = IOException("No connection")
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } throws networkException

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleNotifications(true)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
            assertEquals(ErrorType.NETWORK, state.error.type)
            assertNotNull(state.error.retry)
        }
    }

    @Test
    fun `toggleNotifications handles FCM token failure`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns null

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleNotifications(true)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
            assertEquals(ErrorType.NETWORK, state.error.type)
        }
    }

    @Test
    fun `requestNotificationPermission succeeds`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { fcmService.updateUserToken(any()) } returns Result.success(Unit)

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.requestNotificationPermission()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertEquals(true, state.notificationsEnabled)
            assertEquals("Notifications enabled", state.successMessage)
        }

        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Success<Unit>>(state)
        }

        coVerify { fcmService.getCurrentToken() }
        coVerify { fcmService.updateUserToken("test_token") }
    }

    @Test
    fun `requestNotificationPermission handles error`() = runTest {
        // Given
        val exception = Exception("Permission denied")
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } throws exception

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.requestNotificationPermission()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
            assertNotNull(state.error.retry)
        }
    }

    @Test
    fun `toggleDarkTheme enables dark theme`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { preferencesRepository.setDarkTheme(any()) } just Runs

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleDarkTheme(true)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { preferencesRepository.setDarkTheme(true) }
    }

    @Test
    fun `toggleDarkTheme handles error`() = runTest {
        // Given
        val exception = Exception("Failed to save preference")
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { preferencesRepository.setDarkTheme(any()) } throws exception

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.toggleDarkTheme(true)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
        }
    }

    @Test
    fun `resetThemeToSystemDefault clears theme preference`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { preferencesRepository.clearDarkTheme() } just Runs

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.resetThemeToSystemDefault()
        testScheduler.advanceUntilIdle()

        // Then
        coVerify { preferencesRepository.clearDarkTheme() }
    }

    @Test
    fun `resetThemeToSystemDefault handles error`() = runTest {
        // Given
        val exception = Exception("Failed to clear preference")
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { preferencesRepository.clearDarkTheme() } throws exception

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.resetThemeToSystemDefault()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Error>(state)
        }
    }

    @Test
    fun `clearMessages resets messages and operation state`() = runTest {
        // Given
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"

        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.clearMessages()
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.settingsState.test {
            val state = awaitItem()
            assertNull(state.successMessage)
            assertNull(state.errorMessage)
        }

        viewModel.operationState.test {
            val state = awaitItem()
            assertIs<UiState.Idle>(state)
        }
    }

    @Test
    fun `darkThemeFlow observes preference changes`() = runTest {
        // Given
        val darkThemeFlow = MutableStateFlow<Boolean?>(false)
        coEvery { userRepository.getCurrentUser() } returns testUser
        coEvery { fcmService.getCurrentToken() } returns "test_token"
        coEvery { preferencesRepository.getDarkThemeFlow() } returns darkThemeFlow

        // When
        viewModel = SettingsViewModel(fcmService, userRepository, preferencesRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.darkThemeFlow.test {
            // Initial value
            val initial = awaitItem()
            assertEquals(false, initial)

            // Change value
            darkThemeFlow.value = true
            val updated = awaitItem()
            assertEquals(true, updated)
        }
    }
}

