package com.coparently.app.presentation.common

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The one source of `LocalParentPalette` (UX-15).
 *
 * The defect this item fixed was that the palette was computed and read by nothing; the
 * assertion that matters is that the colours the two parents chose — not the default pink and
 * blue — are what the provider ends up handing to every `ParentColors` call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentPaletteViewModelTest {

    private val me = User(
        id = "u1",
        email = "olya@example.com",
        name = "Olya",
        role = "mom",
        colorCode = ParentColorChoice.ORANGE.storedCode
    )
    private val partner = PartnerSummary(
        id = "u2",
        name = "Pavel",
        email = "pavel@example.com",
        pairedSinceMillis = 1L,
        role = "dad",
        colorCode = ParentColorChoice.PURPLE.storedCode
    )

    /** Every ViewModel a test made, so tear-down can stop what it left running. */
    private val viewModels = mutableListOf<ParentPaletteViewModel>()

    @Before
    fun setUp() {
        // Standard, not unconfined: `ParentsSource` answers from the real `Dispatchers.Default`,
        // and under an unconfined Main its answer ran this ViewModel's `viewModelScope` (the
        // `stateIn` sharing and its `WhileSubscribed` bookkeeping) inline on that thread. A
        // collector cancelled while it was running there left the sharing's next step to that
        // thread, which could run it after `resetMain`: a `delay` on the absent Main dispatcher,
        // reported as a `CompletionHandlerException` by whichever `runTest` was running. Standard
        // queues every such resumption on the test scheduler, which only the test thread runs.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        // Nothing calls onCleared in a test. Cancel while Main is still the test dispatcher, as
        // ExportViewModelTest does, so nothing of this ViewModel outlives the test.
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `starts on the default and then draws what the two parents chose`() = runTest {
        val viewModel = ParentPaletteViewModel(testParentsSource(me = me, partner = partner)).also { viewModels += it }

        // The first frame, before anything collects the parents: what the app always drew.
        assertEquals(ParentPalette.Default, viewModel.palette.value)

        viewModel.palette.test {
            // Whether the collector still sees the default depends on how fast `stateIn` reaches
            // the parents on this scheduler, so the default is allowed and skipped, not required;
            // the value it settles on is the one that matters.
            var palette = awaitItem()
            if (palette == ParentPalette.Default) palette = awaitItem()
            assertEquals(ParentPalette(ParentColorChoice.ORANGE.color, ParentColorChoice.PURPLE.color), palette)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
