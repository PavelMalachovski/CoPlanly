package com.coparently.app.presentation.common

import com.coparently.app.data.chat.OtherFamiliesUnreadSource
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import kotlin.test.assertTrue

/**
 * The family switcher behind the top-bar chip and the Settings row (M-8).
 *
 * Two rules worth pinning: it is offered **at two, not at one**, and the co-parents' names — the
 * one remote read — are fetched once per co-parent rather than on every emission of a row that
 * re-emits whenever any column of it moves. And the cross-family dot: shown for a family not on
 * screen whose chat moved, never for the family on screen and never at one family.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilySwitcherViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val families = MutableStateFlow(listOf(BOB_FAMILY))
    private val source = mockk<SelectedFamilySource>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val unread = MutableStateFlow<Set<String>>(emptySet())
    private val unreadSource = mockk<OtherFamiliesUnreadSource> {
        every { unreadFamilyIds } returns unread
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { userRepository.observeCurrentUserId() } returns flowOf(ALICE)
        every { source.observeFamilies(ALICE) } returns families
        every { source.observe(ALICE) } returns flowOf(BOB_FAMILY)
        coEvery { source.named(any()) } answers {
            firstArg<List<FamilyOption>>().map { it.copy(partnerName = NAMES.getValue(it.partnerUid)) }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one family offers no switcher and costs no read`() = runTest(dispatcher) {
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.canSwitch)
        coVerify(exactly = 0) { source.named(any()) }
    }

    @Test
    fun `a second family offers the switcher, with both co-parents named`() = runTest(dispatcher) {
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        families.value = listOf(BOB_FAMILY, CAROL_FAMILY)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.canSwitch)
        assertEquals(listOf("Bob", "Carol"), state.families.map { it.partnerName })
        assertEquals(BOB_FAMILY.familyId, state.selected?.familyId)
    }

    @Test
    fun `a name is read once, not on every emission`() = runTest(dispatcher) {
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        families.value = listOf(BOB_FAMILY, CAROL_FAMILY)
        advanceUntilIdle()
        // A new list with the same two co-parents in the other order — a real change, so the
        // flow emits it — must not read either profile again.
        families.value = listOf(CAROL_FAMILY, BOB_FAMILY)
        advanceUntilIdle()

        coVerify(exactly = 1) { source.named(any()) }
        assertEquals(listOf("Carol", "Bob"), vm.state.value.families.map { it.partnerName })
    }

    @Test
    fun `a switch goes through the one place that re-points the projection`() = runTest(dispatcher) {
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)

        vm.select(CAROL_FAMILY.familyId)
        advanceUntilIdle()

        coVerify { source.select(CAROL_FAMILY.familyId) }
    }

    @Test
    fun `another family's news raises the chip's dot and that family's row`() = runTest(dispatcher) {
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        families.value = listOf(BOB_FAMILY, CAROL_FAMILY)
        advanceUntilIdle()
        assertFalse(vm.state.value.otherFamilyHasUnread)

        unread.value = setOf(CAROL_FAMILY.familyId)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.otherFamilyHasUnread)
        assertTrue(state.hasUnread(CAROL_FAMILY.familyId))
        assertFalse(state.hasUnread(BOB_FAMILY.familyId))
    }

    @Test
    fun `the family on screen never raises the dot`() = runTest(dispatcher) {
        // The source excludes it too; this is the guard for the moment a switch lands before
        // the source has re-keyed its listeners.
        unread.value = setOf(BOB_FAMILY.familyId)
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        families.value = listOf(BOB_FAMILY, CAROL_FAMILY)
        advanceUntilIdle()

        assertFalse(vm.state.value.otherFamilyHasUnread)
        assertFalse(vm.state.value.hasUnread(BOB_FAMILY.familyId))
    }

    @Test
    fun `one family shows no dot whatever the source says`() = runTest(dispatcher) {
        unread.value = setOf(CAROL_FAMILY.familyId)
        val vm = FamilySwitcherViewModel(source, userRepository, unreadSource)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.otherFamilyHasUnread)
        assertFalse(vm.state.value.hasUnread(CAROL_FAMILY.familyId))
    }

    private companion object {
        const val ALICE = "alice-uid"
        val BOB_FAMILY = FamilyOption("alice-uid__bob-uid", "bob-uid")
        val CAROL_FAMILY = FamilyOption("alice-uid__carol-uid", "carol-uid")
        val NAMES = mapOf("bob-uid" to "Bob", "carol-uid" to "Carol")
    }
}
