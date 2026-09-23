package com.coparently.app.presentation.settings

import com.coparently.app.R
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.feed.CalendarFeedLimitException
import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed
import com.coparently.app.domain.repository.CalendarFeedRepository
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The calendar-feed screen (MON-17): which family it is about, which links it lists, and what it
 * does with the one URL the server ever returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CalendarFeedRepository>()
    private val selectedFamilySource = mockk<SelectedFamilySource> {
        coEvery { selected() } returns FAMILY
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.list() } returns Result.success(listOf(MINE, OTHER_FAMILY_LINK))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lists only the links into the family on screen`() = runTest(dispatcher) {
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        assertEquals(FAMILY.familyId, vm.state.value.familyId)
        assertEquals(listOf(MINE), vm.state.value.links)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `without a family there is nothing to link, and nothing is listed`() = runTest(dispatcher) {
        coEvery { selectedFamilySource.selected() } returns null
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        assertNull(vm.state.value.familyId)
        assertEquals(emptyList<CalendarFeedLink>(), vm.state.value.links)
        coVerify(exactly = 0) { repository.list() }

        vm.create("en")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.create(any(), any()) }
    }

    @Test
    fun `a new link is held for sharing, for the family on screen, in the app language`() = runTest(dispatcher) {
        coEvery { repository.create(FAMILY.familyId, "cs") } returns Result.success(CREATED)
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        vm.create("cs")
        advanceUntilIdle()

        assertEquals(CREATED, vm.state.value.created)
        assertFalse(vm.state.value.isBusy)
        vm.dismissCreated()
        assertNull(vm.state.value.created)
    }

    @Test
    fun `hitting the server's cap says so, rather than blaming the connection`() = runTest(dispatcher) {
        coEvery { repository.create(any(), any()) } returns Result.failure(CalendarFeedLimitException())
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        vm.create("en")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.calendar_feed_limit_reached), vm.state.value.message)
        assertNull(vm.state.value.created)
    }

    @Test
    fun `any other failed create is a connection failure`() = runTest(dispatcher) {
        coEvery { repository.create(any(), any()) } returns Result.failure(IllegalStateException("offline"))
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        vm.create("en")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.calendar_feed_create_failed), vm.state.value.message)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `a revoked link leaves the list, and a just-made one stops being offered`() = runTest(dispatcher) {
        coEvery { repository.create(any(), any()) } returns Result.success(CREATED)
        coEvery { repository.revoke(CREATED.feedId) } returns Result.success(Unit)
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()
        vm.create("en")
        advanceUntilIdle()
        coEvery { repository.list() } returns Result.success(listOf(MINE, JUST_MADE))
        vm.refresh()
        advanceUntilIdle()

        vm.revoke(CREATED.feedId)
        advanceUntilIdle()

        assertEquals(listOf(MINE), vm.state.value.links)
        assertNull(vm.state.value.created)
        assertEquals(UiText.Res(R.string.calendar_feed_revoked), vm.state.value.message)
    }

    @Test
    fun `a refused revoke keeps the link listed`() = runTest(dispatcher) {
        coEvery { repository.revoke(MINE.feedId) } returns Result.failure(IllegalStateException("denied"))
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        vm.revoke(MINE.feedId)
        advanceUntilIdle()

        assertEquals(listOf(MINE), vm.state.value.links)
        assertEquals(UiText.Res(R.string.calendar_feed_revoke_failed), vm.state.value.message)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `a failed list keeps the screen usable and says so`() = runTest(dispatcher) {
        coEvery { repository.list() } returns Result.failure(IllegalStateException("offline"))
        val vm = CalendarFeedViewModel(repository, selectedFamilySource)
        advanceUntilIdle()

        assertEquals(FAMILY.familyId, vm.state.value.familyId)
        assertFalse(vm.state.value.isLoading)
        assertEquals(UiText.Res(R.string.calendar_feed_load_failed), vm.state.value.message)
    }

    private companion object {
        val FAMILY = FamilyOption("alice-uid__bob-uid", "bob-uid")
        val MINE = CalendarFeedLink("feed-1", FAMILY.familyId, createdAtMillis = 1L, lastUsedAtMillis = 2L)
        val OTHER_FAMILY_LINK = CalendarFeedLink("feed-2", "alice-uid__carol-uid", 1L, 1L)
        val CREATED = CreatedCalendarFeed(
            feedId = "feed-3",
            url = "https://example.test/calendarFeed/token.ics",
            webcalUrl = "webcal://example.test/calendarFeed/token.ics"
        )
        val JUST_MADE = CalendarFeedLink(CREATED.feedId, FAMILY.familyId, 3L, 3L)
    }
}
