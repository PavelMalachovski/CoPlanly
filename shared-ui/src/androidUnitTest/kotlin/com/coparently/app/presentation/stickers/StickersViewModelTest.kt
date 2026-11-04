package com.coparently.app.presentation.stickers

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.stickers.ParentRoles
import com.coparently.app.data.stickers.StickerStore
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.presentation.common.testParentsSource
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

/**
 * The stickers a phone shows follow the family on screen — the app's own included — and a sticker
 * is sent to that family's co-parent, read afresh at the moment of sending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StickersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = mockk<StickerStore>(relaxed = true)
    private val roles = mockk<ParentRoles>(relaxed = true)
    private val view = MutableStateFlow(FamilyView(me = "uA", onScreen = "uA__uB", others = setOf("uA__uC")))
    private val familyViewSource = mockk<FamilyViewSource> { every { observe() } returns view }

    private fun sticker(id: String, family: String, kind: StickerKind = StickerKind.THANK_YOU, seen: Long? = null) =
        Sticker(id, "uA", if (kind.fromApp) null else "uB", family, kind, createdAtMillis = 1L, seenAtMillis = seen)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { store.signedInUid() } returns "uA"
        every { roles.observeMine() } returns flowOf(ParentRoleAnswer.Unanswered)
        every { store.observeSent(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StickersViewModel(store, roles, familyViewSource, testParentsSource())

    @Test
    fun `another live family's stickers wait until it is on screen, the app's do not`() = runTest(dispatcher) {
        every { store.observeReceived("uA") } returns flowOf(
            listOf(
                sticker("ours", "uA__uB"),
                sticker("theirs", "uA__uC"),
                sticker("app", "", StickerKind.ON_TIME),
                sticker("seen", "uA__uB", seen = 5L)
            )
        )
        val vm = viewModel()
        backgroundScope.launch { vm.received.collect {} }
        backgroundScope.launch { vm.unseen.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("ours", "app", "seen"), vm.received.value.map { it.id })
        assertEquals(listOf("ours", "app"), vm.unseen.value.map { it.id })

        view.value = FamilyView(me = "uA", onScreen = "uA__uC", others = setOf("uA__uB"))
        advanceUntilIdle()
        assertEquals(listOf("theirs", "app"), vm.received.value.map { it.id })
    }

    @Test
    fun `a sticker goes to the family on screen, and a failure says so`() = runTest(dispatcher) {
        every { store.observeReceived(any()) } returns flowOf(emptyList())
        coEvery { store.send("uA__uB", StickerKind.GOOD_TEAM) } returns true
        val vm = viewModel()
        vm.send(StickerKind.GOOD_TEAM)
        advanceUntilIdle()
        assertEquals(StickersViewModel.SendState.Sent, vm.sending.value)

        vm.sendingShown()
        coEvery { store.send(any(), any()) } returns false
        vm.send(StickerKind.THANK_YOU)
        advanceUntilIdle()
        assertEquals(StickersViewModel.SendState.Failed, vm.sending.value)
    }

    @Test
    fun `with no family on screen nothing is sent`() = runTest(dispatcher) {
        every { store.observeReceived(any()) } returns flowOf(emptyList())
        view.value = FamilyView(me = "uA", onScreen = null, others = emptySet())
        val vm = viewModel()
        vm.send(StickerKind.THANK_YOU)
        advanceUntilIdle()
        assertEquals(StickersViewModel.SendState.Failed, vm.sending.value)
        coVerify(exactly = 0) { store.send(any(), any()) }
    }

    @Test
    fun `an answer that did not save keeps the question`() = runTest(dispatcher) {
        every { store.observeReceived(any()) } returns flowOf(emptyList())
        coEvery { roles.setMine(any()) } returns false
        val vm = viewModel()
        vm.setRole(ParentRole.MOM)
        advanceUntilIdle()
        assertEquals(true, vm.roleSaveFailed.value)
    }
}
