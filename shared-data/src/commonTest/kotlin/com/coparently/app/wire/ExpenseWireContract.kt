@file:OptIn(InternalSharedApi::class)

package com.coparently.app.wire

import com.coparently.app.data.repository.expenseFromDocument
import com.coparently.app.data.repository.expenseToFirestoreMap
import com.coparently.app.data.repository.toDomain
import com.coparently.app.data.repository.toEntity
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * `expenses`: read by `expenseFromDocument` (`ExpenseRepositoryImpl`'s listener), stored through
 * Room (`toEntity`/`toDomain`) and written back by `expenseToFirestoreMap` — a `set()` stamped with
 * the document's own owner, as `updateExpense` does. FAM-2's rule is the one this pins hardest: an
 * unrecognised `forMembers` reference comes back out.
 *
 * The mappers are top-level in `ExpenseMappers.kt` since MON-24 phase 2 (Task 8f), so this contract
 * builds no repository and runs on the iPhone's simulator too. It reads no wall clock: an expense's
 * dates are naive on the wire and in the domain alike.
 */
internal object ExpenseWireContract : WireContract {

    override val collection = "expenses"

    override val alwaysWrites = setOf(
        "id", "forMembers", "familyId", "title", "amount", "currency", "category", "createdByFirebaseUid",
        "paidBy", "splitBetween", "date", "createdAt", "splitBasisPoints"
    )

    override val blankMeansAbsent = setOf("familyId", "receiptUrl", "notes")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val expense = runCatching { expenseFromDocument(document) }.getOrNull() ?: return null
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
        return expenseToFirestoreMap(expenseFromDocument(document).toEntity().toDomain(), ownerUid = owner)
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "ExpenseRepositoryImpl.addExpense's document: shared, priced at an agreed split, tagged.",
            document = expenseToFirestoreMap(expense(), ownerUid = "uidA")
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
        date = LocalDate(2026, 5, 10),
        receiptUrl = "ph1|receipts/uidA__uidB/exp-current-1/7a1c9b04.jpg|image/jpeg|48213|" +
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        notes = "Spring term",
        createdAt = LocalDateTime(2026, 5, 10, 12, 0),
        createdByFirebaseUid = "uidA",
        splitBasisPoints = 6000,
        familyId = "uidA__uidB"
    )
}
