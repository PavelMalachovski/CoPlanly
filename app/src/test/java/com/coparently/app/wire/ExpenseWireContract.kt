package com.coparently.app.wire

import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `expenses`: read by `ExpenseRepositoryImpl.expenseFromDocument` (the listener), stored through
 * Room and written back by `expenseToFirestoreMap` — a `set()` stamped with the document's own
 * owner, as `updateExpense` does. FAM-2's rule is the one this pins hardest: an unrecognised
 * `forMembers` reference comes back out.
 */
internal object ExpenseWireContract : WireContract {

    private val repository by lazy { ExpenseRepositoryImpl(mockk(), mockk(), mockk(), mockk(), mockk()) }

    override val collection = "expenses"

    override val alwaysWrites = setOf(
        "id", "forMembers", "familyId", "title", "amount", "currency", "category", "createdByFirebaseUid",
        "paidBy", "splitBetween", "date", "createdAt", "splitBasisPoints"
    )

    override val blankMeansAbsent = setOf("familyId", "receiptUrl", "notes")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val expense = runCatching { repository.expenseFromDocument(document) }.getOrNull() ?: return null
        return mapOf(
            "id" to expense.id,
            "title" to expense.title,
            "amount" to expense.amount,
            "currency" to expense.currency,
            "category" to expense.category.name,
            "paidBy" to expense.paidBy,
            "splitBetween" to expense.splitBetween,
            "date" to expense.date.toString(),
            "forMembers" to FamilyMemberRef.store(expense.forMembers),
            "familyId" to expense.familyId,
            "splitBasisPoints" to expense.splitBasisPoints,
            "createdByFirebaseUid" to expense.createdByFirebaseUid,
            "deleted" to false
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return null
        val owner = (document["createdByFirebaseUid"] as? String).orEmpty()
        return with(repository) {
            expenseToFirestoreMap(expenseFromDocument(document).toEntity().toDomain(), ownerUid = owner)
        }
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "ExpenseRepositoryImpl.addExpense's document: shared, priced at an agreed split, tagged.",
            document = repository.expenseToFirestoreMap(expense(), ownerUid = "uidA")
        )
    )

    private fun expense() = Expense(
        id = "exp-current-1",
        forMembers = listOf(
            FamilyMemberRef.Child("c1"),
            FamilyMemberRef.Pet("p1"),
            FamilyMemberRef.Unknown("future:xyz")
        ),
        title = "Swimming course",
        amount = 1250.5,
        currency = "CZK",
        category = ExpenseCategory.ACTIVITIES,
        paidBy = "uidA",
        splitBetween = listOf("uidA", "uidB"),
        date = LocalDate.of(2026, 5, 10),
        receiptUrl = "receipts/uidA/exp-current-1.jpg",
        notes = "Spring term",
        createdAt = LocalDateTime.of(2026, 5, 10, 12, 0),
        createdByFirebaseUid = "uidA",
        splitBasisPoints = 6000,
        familyId = "uidA__uidB"
    )
}
