package com.coparently.app.domain.repository

import com.coparently.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for managing expenses.
 * Part of the domain layer in Clean Architecture.
 */
interface ExpenseRepository {
    /**
     * Gets all expenses as a Flow.
     */
    fun getAllExpenses(): Flow<List<Expense>>

    /**
     * Every expense this phone holds, in every family, live or ended. Only for the export and
     * Family → Earlier families. Defaults to [getAllExpenses] for a fake.
     */
    fun getAllExpensesInEveryFamily(): Flow<List<Expense>> = getAllExpenses()

    /**
     * Gets expenses for a specific date range as a Flow.
     */
    fun getExpensesForPeriod(start: LocalDate, end: LocalDate): Flow<List<Expense>>

    /**
     * Gets an expense by ID.
     */
    suspend fun getExpenseById(id: String): Expense?

    /**
     * Adds a new expense.
     */
    suspend fun addExpense(expense: Expense)

    /**
     * Updates an existing expense.
     */
    suspend fun updateExpense(expense: Expense)

    /**
     * Deletes an expense.
     */
    suspend fun deleteExpense(expenseId: String)

    /**
     * Tells the co-parent, in their family's thread, that this parent questions an expense the
     * co-parent recorded (second audit: one parent could enter costs and the app called the other a
     * debtor, with no way to say "not this one"). Only the creator changes an expense, so the
     * question is a card that stays in the record; the reason is said in the chat.
     *
     * @return Whether the card was sent: false for this parent's own expense, or one whose family
     *   has no co-parent to tell.
     */
    suspend fun questionExpense(expense: Expense): Boolean

    /**
     * Syncs expenses with Firestore.
     */
    /**
     * Mirrors the remote side into Room and **never returns** — it collects a snapshot
     * listener for as long as its caller's scope lives.
     *
     * Named for the shape rather than for the subject (CQ-10). It was `syncWithFirestore()`,
     * the same name the one-shot repositories use, which made it look safe to await from
     * `SyncService.performFullSync()`. It is not: that call would hang, `SyncWorker` would be
     * killed at WorkManager's ten-minute ceiling, and sync would stop entirely with no
     * exception and no log. Call it from a scope that is allowed to run forever.
     */
    suspend fun observeRemote()
}
