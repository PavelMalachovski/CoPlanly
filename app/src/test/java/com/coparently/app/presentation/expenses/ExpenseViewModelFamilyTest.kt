package com.coparently.app.presentation.expenses

import com.coparently.app.data.family.RecordPlace
import com.coparently.app.data.family.familyRow
import com.coparently.app.data.family.testFamilyViewSource
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.FamilySplit
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.User
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.testFamilyMembersSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import kotlin.test.assertNull

/**
 * The Expenses screen of a parent in two families (October 2026 isolation audit).
 *
 * Zuzana co-parents with Igor and with Carol. The switcher chip sits in the Expenses top bar, so
 * this ViewModel outlives a switch — and a push tap can switch families while a save is half done.
 * Every decision here must belong to one family: the one whose figures were on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelFamilyTest {

    private val dispatcher = StandardTestDispatcher()

    /** The co-parent of the family on screen, as the switcher has written it into Zuzana's row. */
    private var onScreen = CAROL

    /** Zuzana's Room row as the family view reads it; a new value is a switch. */
    private val row = MutableStateFlow<UserEntity?>(familyRow(ZUZANA, onScreen = CAROL, partners = listOf(IGOR, CAROL)))

    private val users = mockk<UserRepository>(relaxed = true) {
        coEvery { getCurrentUserId() } returns ZUZANA
        coEvery { getUserById(ZUZANA) } answers { zuzana() }
        every { observeCurrentUserId() } returns flowOf(ZUZANA)
        every { getAllUsers() } returns flowOf(emptyList())
    }
    private val expenseRepository = mockk<ExpenseRepository>(relaxed = true) {
        every { getAllExpenses() } returns flowOf(emptyList())
    }
    private val receiptStorage = mockk<RecordPhotoStorage>(relaxed = true)
    private val shownSplit = MutableStateFlow(FamilySplit.UNKNOWN)
    private val familySettingsRepository = mockk<FamilySettingsRepository>(relaxed = true) {
        every { observeSplit() } returns shownSplit
        every { agreedRatioOrDefault(IGOR_FAMILY) } returns SplitRatio(IGOR_SPLIT)
        every { agreedRatioOrDefault(CAROL_FAMILY) } returns SplitRatio(CAROL_SPLIT)
        coEvery { acceptProposal(any()) } returns Result.success(Unit)
        coEvery { declineProposal(any()) } returns Result.success(Unit)
        coEvery { withdrawProposal(any()) } returns Result.success(Unit)
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
    fun `an answer goes to the family whose proposal was on screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch(dispatcher) { viewModel.pendingRatioProposal.collect {} }
        shownSplit.value = FamilySplit(IGOR_FAMILY, proposalBy(IGOR, IGOR_FAMILY), SplitRatio.EVEN)
        advanceUntilIdle()
        val shown = checkNotNull(viewModel.pendingRatioProposal.value)
        assertEquals(IGOR_FAMILY, shown.familyId)

        // A push tap switches to the family with Carol before the tap on Confirm lands.
        onScreen = CAROL
        shownSplit.value = FamilySplit(CAROL_FAMILY, null, SplitRatio.EVEN, answered = false)
        viewModel.decideRatioProposal(shown, accept = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { familySettingsRepository.acceptProposal(IGOR_FAMILY) }
        coVerify(exactly = 0) { familySettingsRepository.acceptProposal(CAROL_FAMILY) }
    }

    @Test
    fun `a family whose document has not answered shows no proposal`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch(dispatcher) { viewModel.pendingRatioProposal.collect {} }
        shownSplit.value = FamilySplit(IGOR_FAMILY, proposalBy(IGOR, IGOR_FAMILY), SplitRatio.EVEN)
        advanceUntilIdle()

        shownSplit.value = FamilySplit(CAROL_FAMILY, null, SplitRatio.EVEN, answered = false)
        advanceUntilIdle()

        assertNull(viewModel.pendingRatioProposal.value, "Igor's proposal must not linger in the family with Carol")
    }

    @Test
    fun `withdrawing takes the proposal back from its own family`() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch(dispatcher) { viewModel.myPendingRatioProposal.collect {} }
        shownSplit.value = FamilySplit(CAROL_FAMILY, proposalBy(ZUZANA, CAROL_FAMILY), SplitRatio.EVEN)
        advanceUntilIdle()

        viewModel.withdrawRatioProposal(checkNotNull(viewModel.myPendingRatioProposal.value))
        advanceUntilIdle()

        coVerify(exactly = 1) { familySettingsRepository.withdrawProposal(CAROL_FAMILY) }
    }

    @Test
    fun `a switch during the receipt upload leaves the expense in the family it was started in`() =
        runTest(dispatcher) {
            // The upload is the slow part of a save; a push tap switches to the family with Igor
            // while it runs. Everything the save decides was read once, before it.
            coEvery { receiptStorage.upload(RecordPhotoKind.RECEIPT, any(), any(), RECEIPT_URI) } answers {
                onScreen = IGOR
                "ph1|receipts/stored"
            }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.addExpense(
                title = "Swimming lessons",
                amount = 1_200.0,
                category = ExpenseCategory.OTHER,
                currency = SupportedCurrency.DEFAULT.code,
                receiptImageUri = RECEIPT_URI
            )
            advanceUntilIdle()

            coVerify { receiptStorage.upload(RecordPhotoKind.RECEIPT, any(), CAROL_FAMILY, RECEIPT_URI) }
            val saved = slot<Expense>()
            coVerify { expenseRepository.addExpense(capture(saved)) }
            assertEquals(CAROL_FAMILY, saved.captured.familyId)
            assertEquals(listOf(ZUZANA, CAROL), saved.captured.splitBetween)
            assertEquals(CAROL_SPLIT, saved.captured.splitBasisPoints, "priced at Carol's agreement, not Igor's")
        }

    @Test
    fun `the figures count the family's own expenses, and mark the rest`() = runTest(dispatcher) {
        // Owner decision, October 2026: an expense that is "only mine" (no family while Zuzana has
        // two) or from an earlier family stays in the list, marked, and is added into nothing.
        val today = java.time.LocalDate.now()
        every { expenseRepository.getAllExpenses() } returns flowOf(
            listOf(
                expense("swimming", 1_200.0, familyId = CAROL_FAMILY, createdBy = CAROL, date = today),
                expense("only-mine", 500.0, familyId = null, createdBy = ZUZANA, date = today),
                expense("earlier", 300.0, familyId = FamilyKey.of(ZUZANA, "uid-ex"), createdBy = ZUZANA, date = today)
            )
        )
        val viewModel = viewModel()
        backgroundScope.launch(dispatcher) { viewModel.monthOfExpenses.collect {} }
        backgroundScope.launch(dispatcher) { viewModel.breakdowns.collect {} }
        advanceUntilIdle()

        val month = viewModel.monthOfExpenses.value
        assertEquals(setOf("swimming", "only-mine", "earlier"), month.expenses.map { it.id }.toSet())
        assertEquals(listOf("swimming"), month.counted.map { it.id })
        assertEquals(1_200.0, month.balances.single().balance.total)
        assertEquals(
            mapOf("only-mine" to RecordPlace.ONLY_ME, "earlier" to RecordPlace.EARLIER_FAMILY),
            month.places
        )
        assertEquals(CAROL_FAMILY, month.familyId)
        assertEquals(1_200.0, viewModel.breakdowns.value.single().total)
    }

    @Test
    fun `the download follows a switch and ends the previous family's listener`() = runTest(dispatcher) {
        var started = 0
        var ended = 0
        coEvery { expenseRepository.observeRemote() } coAnswers {
            started++
            try {
                awaitCancellation()
            } finally {
                ended++
            }
        }
        viewModel()
        advanceUntilIdle()
        assertEquals(1, started)

        row.value = familyRow(ZUZANA, onScreen = IGOR, partners = listOf(IGOR, CAROL))
        advanceUntilIdle()
        assertEquals(2, started, "the family with Igor gets its own listener")
        assertEquals(1, ended, "the family with Carol's listener ends")

        // A change that leaves the same family on screen does not restart the download.
        row.value = familyRow(ZUZANA, onScreen = IGOR, partners = listOf(IGOR, CAROL, "uid-dan"))
        advanceUntilIdle()
        assertEquals(2, started)
    }

    private fun viewModel() = ExpenseViewModel(
        expenseRepository,
        users,
        receiptStorage,
        mockk(relaxed = true) {
            every { getDefaultCurrencyFlow() } returns flowOf(SupportedCurrency.DEFAULT)
        },
        mockk(relaxed = true),
        familySettingsRepository,
        ParentsSource(users, mockk<PairingRepository>(relaxed = true)),
        testFamilyMembersSource(),
        testFamilyViewSource(ZUZANA, row)
    )

    private fun zuzana() = User(
        id = ZUZANA,
        email = "zuzana@example.test",
        name = "Zuzana",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = onScreen,
        partnerIds = listOf(IGOR, CAROL)
    )

    /** A shared expense of [amount] CZK in [familyId], written and paid by [createdBy]. */
    private fun expense(
        id: String,
        amount: Double,
        familyId: String?,
        createdBy: String,
        date: java.time.LocalDate
    ) = Expense(
        id = id,
        title = id,
        amount = amount,
        currency = "CZK",
        category = ExpenseCategory.CLOTHING,
        paidBy = createdBy,
        splitBetween = listOf(ZUZANA, CAROL),
        date = date,
        createdByFirebaseUid = createdBy,
        familyId = familyId
    )

    /** [familyId]'s settings with an even split agreed and [by]'s 70/30 proposal pending. */
    private fun proposalBy(by: String, familyId: String) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = familyId.split(FamilyKey.SEPARATOR),
        lastModifiedBy = by,
        lastModifiedAtMillis = 1L,
        proposal = SplitRatioProposal(ratio = SplitRatio(IGOR_SPLIT), proposedBy = by, proposedAtMillis = 1L)
    )

    private companion object {
        const val ZUZANA = "uid-zuzana"
        const val IGOR = "uid-igor"
        const val CAROL = "uid-carol"
        val IGOR_FAMILY = FamilyKey.of(ZUZANA, IGOR)
        val CAROL_FAMILY = FamilyKey.of(ZUZANA, CAROL)
        const val IGOR_SPLIT = 7_000
        const val CAROL_SPLIT = 6_000
        const val RECEIPT_URI = "content://receipt"
    }
}
