package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.expenses.calculateExpenseBalance
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * A shared expense recorded on one phone produces a debt on the **other** phone.
 *
 * The defect class this guards is CLAUDE.md's "`Expense.splitBetween` used to be empty on every
 * row production ever wrote": the payer's month looked right, the co-parent's showed nothing owed,
 * and the unit suite missed it because its fixture already named both parents. Here nothing is a
 * fixture. Alice's `splitBetween` comes from `ParentsSource.coParentUid()` — the suspend accessor
 * `ExpenseViewModel.sharedWith` reads (item 17) — Alice's phone writes through
 * `ExpenseRepositoryImpl.addExpense`, Bob's phone downloads through `observeRemote`'s own
 * `familyId` listener, and Bob's balance is computed from what landed in *his* Room.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentExpensesTest : TwoParentTest() {

    @Test
    fun bobOwesHalfOfWhatAlicePaid() = runBlocking<Unit> {
        // Exactly what `ExpenseViewModel.sharedWith(userId)` builds for a shared expense.
        val splitBetween = listOfNotNull(
            alice.uid,
            alice.parentsSource.coParentUid()?.takeIf { it != alice.uid }
        )
        assertEquals("Alice's save path did not find her co-parent", 2, splitBetween.size)

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            title = "School trip",
            amount = AMOUNT,
            currency = "EUR",
            category = ExpenseCategory.EDUCATION,
            paidBy = alice.uid,
            splitBetween = splitBetween,
            splitBasisPoints = HALF_BASIS_POINTS
        )
        alice.expenseRepository.addExpense(expense)

        val downloaded = downloadOnBobsPhone(expense.id)
        assertEquals(setOf(alice.uid, bob.uid), downloaded.splitBetween.toSet())

        val roles = mapOf(
            alice.uid to checkNotNull(bob.userRepository.getRemoteUserProfile(alice.uid)).role,
            bob.uid to checkNotNull(bob.userRepository.getRemoteUserProfile(bob.uid)).role
        )
        val bobsMonth = calculateExpenseBalance(listOf(downloaded), bob.uid, roles)
        assertTrue("both slots must be known once paired", bobsMonth.splitKnown)
        assertEquals(-AMOUNT / 2, bobsMonth.netForCurrentUser, DELTA)

        val alicesMonth = calculateExpenseBalance(
            alice.expenseRepository.getAllExpenses().first(),
            alice.uid,
            roles
        )
        assertEquals(AMOUNT / 2, alicesMonth.netForCurrentUser, DELTA)
    }

    /** Runs Bob's expense listener until [expenseId] is in his Room, then stops it. */
    private suspend fun downloadOnBobsPhone(expenseId: String): Expense = coroutineScope {
        val listener = launch { bob.expenseRepository.observeRemote() }
        try {
            withTimeout(EmulatorParent.WAIT_MS) {
                bob.expenseRepository.getAllExpenses()
                    .first { list -> list.any { it.id == expenseId } }
                    .single { it.id == expenseId }
            }
        } finally {
            listener.cancel()
        }
    }

    private companion object {
        const val AMOUNT = 120.0
        const val DELTA = 0.001

        /** Slot 1's share, in basis points: an even split, the default agreement. */
        const val HALF_BASIS_POINTS = 5_000
    }
}
