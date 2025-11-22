package com.coparently.app.domain.repository

import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.ExpenseSummary
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
     * Gets expenses for a specific child as a Flow.
     */
    fun getExpensesForChild(childId: String): Flow<List<Expense>>

    /**
     * Gets expenses for a specific date range as a Flow.
     */
    fun getExpensesForPeriod(start: LocalDate, end: LocalDate): Flow<List<Expense>>

    /**
     * Gets expenses by category as a Flow.
     */
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>>

    /**
     * Gets an expense by ID.
     */
    suspend fun getExpenseById(id: String): Expense?

    /**
     * Gets expense summary for a specific period.
     */
    suspend fun getExpenseSummary(start: LocalDate, end: LocalDate): ExpenseSummary

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
     * Syncs expenses with Firestore.
     */
    suspend fun syncWithFirestore()
}
