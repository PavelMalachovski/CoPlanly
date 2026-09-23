package com.coparently.app.presentation.settings

import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiState
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The first tests [SettingsViewModel] has had (CLAUDE.md: "Settings still has none — that is the
 * one to write when touching it"). They pin the push switch, which used to change the switch and
 * nothing else, and whose failures nothing on the screen reported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fcmService = mockk<FcmService>(relaxed = true)
    private val pauseBeforeSending = MutableStateFlow(false)
    private val preferences = mockk<PreferencesRepository>(relaxed = true) {
        every { getDarkThemeFlow() } returns flowOf(null)
        every { getDefaultCurrencyFlow() } returns flowOf(SupportedCurrency.DEFAULT)
        every { getPauseBeforeSendingFlow() } returns pauseBeforeSending
        coEvery { setPauseBeforeSending(any()) } answers { pauseBeforeSending.value = firstArg() }
    }

    private fun viewModel(): SettingsViewModel {
        val userRepository = mockk<UserRepository>(relaxed = true) {
            coEvery { getCurrentUser() } returns null
        }
        return SettingsViewModel(
            fcmService = fcmService,
            accountDeletionService = mockk(relaxed = true),
            userRepository = userRepository,
            preferencesRepository = preferences,
            analyticsManager = mockk(relaxed = true),
            signedInAccountSource = mockk(relaxed = true),
            familyKindSource = mockk(relaxed = true),
            parentsSource = mockk(relaxed = true),
            familySettingsRepository = mockk(relaxed = true)
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the switch shows the stored choice, not whether a token exists`() = runTest(dispatcher) {
        every { fcmService.isPushEnabled() } returns false

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.settingsState.value.notificationsEnabled)
    }

    @Test
    fun `turning push off goes through the stored switch`() = runTest(dispatcher) {
        every { fcmService.isPushEnabled() } returns true
        coEvery { fcmService.setPushEnabled(false) } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleNotifications(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { fcmService.setPushEnabled(false) }
        assertFalse(vm.settingsState.value.notificationsEnabled)
        assertTrue(vm.operationState.value is UiState.Success)
    }

    @Test
    fun `a failed switch reports an error and leaves the switch where it was`() = runTest(dispatcher) {
        every { fcmService.isPushEnabled() } returns false
        coEvery { fcmService.setPushEnabled(true) } returns Result.failure(IllegalStateException("no token"))

        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleNotifications(true)
        advanceUntilIdle()

        assertTrue(vm.operationState.value is UiState.Error)
        assertEquals(false, vm.settingsState.value.notificationsEnabled)
    }

    @Test
    fun `pause before sending is off until turned on, and turning it on is stored`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertFalse(vm.pauseBeforeSending.value)

        vm.setPauseBeforeSending(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferences.setPauseBeforeSending(true) }
        assertTrue(vm.pauseBeforeSending.value)
    }
}
