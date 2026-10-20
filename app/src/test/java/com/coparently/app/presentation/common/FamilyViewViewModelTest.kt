package com.coparently.app.presentation.common

import app.cash.turbine.test
import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/** The view `MainActivity` provides as `LocalFamilyView` follows the source, everything first. */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the view starts with everything and follows a switch`() = runTest(dispatcher) {
        val igor = FamilyView(me = "zuzana", onScreen = "igor__zuzana", others = setOf("carol__zuzana"))
        val carol = FamilyView(me = "zuzana", onScreen = "carol__zuzana", others = setOf("igor__zuzana"))
        val views = MutableStateFlow(igor)
        val source = mockk<FamilyViewSource> { every { observe() } returns views }

        FamilyViewViewModel(source).view.test {
            // The initial value, until the source answers; a collector may or may not see it.
            val first = awaitItem()
            assertEquals(igor, if (first == FamilyView.EVERYTHING) awaitItem() else first)
            views.value = carol
            assertEquals(carol, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
