package com.coparently.app.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.model.ExpenseSummary
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.ReceiptStorage
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * State of the "save expense" operation, driving the Add Expense screen.
 */
sealed interface ExpenseSaveState {
    data object Idle : ExpenseSaveState
    data object Saving : ExpenseSaveState

    /**
     * Expense stored locally (and synced when online).
     * [warning] is non-null when the receipt photo upload failed —
     * the expense itself was still saved, just without the receipt.
     */
    data class Saved(val warning: String? = null) : ExpenseSaveState
}

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val userRepository: UserRepository,
    private val receiptStorage: ReceiptStorage
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String>("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUserId.value = user.id
                expenseRepository.syncWithFirestore()
            }
        }
    }

    val expenses: StateFlow<List<Expense>> = expenseRepository.getAllExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _expenseSummary = MutableStateFlow<ExpenseSummary?>(null)
    val expenseSummary: StateFlow<ExpenseSummary?> = _expenseSummary.asStateFlow()

    private val _saveState = MutableStateFlow<ExpenseSaveState>(ExpenseSaveState.Idle)
    val saveState: StateFlow<ExpenseSaveState> = _saveState.asStateFlow()

    init {
        // Load initial summary for current month
        loadSummaryForMonth(LocalDate.now())
    }

    fun loadSummaryForMonth(date: LocalDate) {
        val start = date.withDayOfMonth(1)
        val end = date.withDayOfMonth(date.lengthOfMonth())

        viewModelScope.launch {
            _expenseSummary.value = expenseRepository.getExpenseSummary(start, end)
        }
    }

    /**
     * Saves a new expense. When [receiptImageUri] is provided, the photo is uploaded
     * to remote storage first and its download URL stored on the expense, so the
     * other parent sees the receipt too. An upload failure does not lose the expense —
     * it is saved without the receipt and a warning is surfaced via [saveState].
     */
    @Suppress("LongParameterList") // mirrors the Expense domain model fields
    fun addExpense(
        title: String,
        amount: Double,
        category: ExpenseCategory,
        childId: String? = null,
        date: LocalDate = LocalDate.now(),
        notes: String? = null,
        receiptImageUri: String? = null
    ) {
        val userId = _currentUserId.value
        if (userId.isEmpty()) return
        if (_saveState.value is ExpenseSaveState.Saving) return

        viewModelScope.launch {
            _saveState.value = ExpenseSaveState.Saving

            val expenseId = UUID.randomUUID().toString()
            var receiptUrl: String? = null
            var warning: String? = null
            if (receiptImageUri != null) {
                receiptUrl = try {
                    receiptStorage.uploadReceipt(expenseId, receiptImageUri)
                } catch (
                    // Any upload failure (IO, storage, decode) must not lose the expense
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    warning = "Receipt upload failed — expense saved without receipt"
                    null
                }
            }

            val expense = Expense(
                id = expenseId,
                childId = childId,
                title = title,
                amount = amount,
                category = category,
                paidBy = userId,
                date = date,
                receiptUrl = receiptUrl,
                notes = notes,
                createdAt = LocalDateTime.now()
            )
            expenseRepository.addExpense(expense)

            // Refresh summary
            loadSummaryForMonth(date)
            _saveState.value = ExpenseSaveState.Saved(warning)
        }
    }

    /** Resets [saveState] back to [ExpenseSaveState.Idle] after the UI consumed a result. */
    fun resetSaveState() {
        _saveState.value = ExpenseSaveState.Idle
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            expenseRepository.deleteExpense(expenseId)
            if (expense?.receiptUrl != null) {
                // Best-effort cleanup; an orphaned photo must not block the delete.
                runCatching { receiptStorage.deleteReceipt(expenseId) }
            }
            // Refresh summary
            loadSummaryForMonth(LocalDate.now())
        }
    }
}
