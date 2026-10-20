package com.coparently.app.presentation.settings

import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.FamilySplit
import com.coparently.app.data.repository.RatioSubmission
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.model.User
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiState
import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.presentation.theme.ParentColorChoice
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

    /**
     * The split's repository. Stubbed and verified with concrete ratios, never `any()`: MockK builds
     * a matcher's signature for a value class from a random Int, and `SplitRatio` refuses one
     * outside 0..10000.
     */
    private val familySettingsRepository = mockk<FamilySettingsRepository>(relaxed = true) {
        coEvery { submitRatio(SplitRatio(SEVENTY)) } returns Result.success(RatioSubmission.APPLIED)
        coEvery { submitRatio(SplitRatio(SEVENTY), "u1__u2", WEEK) } returns Result.success(RatioSubmission.PROPOSED)
    }

    private fun viewModel(
        userRepository: UserRepository = mockk<UserRepository>(relaxed = true) {
            coEvery { getCurrentUser() } returns null
        }
    ): SettingsViewModel {
        return SettingsViewModel(
            fcmService = fcmService,
            accountDeletionService = mockk(relaxed = true),
            userRepository = userRepository,
            preferencesRepository = preferences,
            analyticsManager = mockk(relaxed = true),
            signedInAccountSource = mockk(relaxed = true),
            familyKindSource = mockk(relaxed = true),
            parentsSource = mockk(relaxed = true),
            familySettingsRepository = familySettingsRepository
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

    @Test
    fun `choosing a country suggests its currency, and never overrides one as a choice`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setCountry(HolidayCountry.SLOVAKIA)
        advanceUntilIdle()

        // A suggestion: the repository keeps a currency the parent picked in Settings themselves.
        coVerify(exactly = 1) { preferences.suggestDefaultCurrency(SupportedCurrency.EUR) }
        coVerify(exactly = 0) { preferences.setDefaultCurrency(any()) }
    }

    @Test
    fun `a colour picked on the wheel is saved as its hue`() = runTest(dispatcher) {
        // Saved to this parent's own profile, which the co-parent's phone listens to: the colour
        // reaches them as soon as this write lands.
        val users = signedIn(colorCode = ParentColorChoice.PINK.storedCode)
        val vm = viewModel(users)

        vm.setParentColor(ParentColor.custom(200))
        advanceUntilIdle()

        coVerify(exactly = 1) { users.updateUser(match { it.colorCode == "hue1|200" }) }
    }

    @Test
    fun `a preset is saved as its stored code`() = runTest(dispatcher) {
        val users = signedIn(colorCode = "hue1|200")
        val vm = viewModel(users)

        vm.setParentColor(ParentColorChoice.ORANGE.color)
        advanceUntilIdle()

        coVerify(exactly = 1) { users.updateUser(match { it.colorCode == ParentColorChoice.ORANGE.storedCode }) }
    }

    @Test
    fun `choosing the colour already saved writes nothing`() = runTest(dispatcher) {
        val users = signedIn(colorCode = "hue1|200")
        val vm = viewModel(users)

        vm.setParentColor(ParentColor.custom(200))
        advanceUntilIdle()

        coVerify(exactly = 0) { users.updateUser(any()) }
    }

    @Test
    fun `a country whose currency the app does not offer suggests none`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setCountry(HolidayCountry.UKRAINE)
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences.suggestDefaultCurrency(any()) }
    }

    @Test
    fun `a split put to the co-parent carries the deadline picked, to the family that was shown`() =
        runTest(dispatcher) {
            val agreed = FamilySettings(
                ratio = SplitRatio.EVEN,
                participants = listOf("u1", "u2"),
                lastModifiedBy = "u2",
                lastModifiedAtMillis = 1L
            )
            val shown = FamilySplit(familyId = "u1__u2", settings = agreed, ratio = SplitRatio.EVEN)
            val vm = viewModel()

            vm.submitRatio(SplitRatio(SEVENTY), shown, WEEK)
            advanceUntilIdle()

            coVerify(exactly = 1) { familySettingsRepository.submitRatio(SplitRatio(SEVENTY), "u1__u2", WEEK) }
        }

    @Test
    fun `a split chosen before anything loaded goes to the family on screen, with no deadline`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.submitRatio(SplitRatio(SEVENTY), FamilySplit.UNKNOWN, answerBy = null)
            advanceUntilIdle()

            coVerify(exactly = 1) { familySettingsRepository.submitRatio(SplitRatio(SEVENTY)) }
            coVerify(exactly = 0) { familySettingsRepository.submitRatio(SplitRatio(SEVENTY), null, null) }
        }

    /** A user repository with a signed-in parent whose stored colour is [colorCode]. */
    private fun signedIn(colorCode: String): UserRepository = mockk(relaxed = true) {
        coEvery { getCurrentUser() } returns User(
            id = "u1",
            email = "pavel@example.com",
            name = "Pavel",
            role = "dad",
            colorCode = colorCode
        )
    }

    private companion object {
        /** 70 % for slot 1, in basis points. */
        const val SEVENTY = 7_000

        /** "A week" from the deadline's chips. */
        val WEEK: AnswerByPick = AnswerByPick.Preset(AnswerByPreset.WEEK)
    }
}
