package com.coparently.app.wire

import com.coparently.app.data.repository.BudgetRepositoryImpl
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Budget
import com.coparently.app.domain.model.ExpenseCategory
import io.mockk.mockk
import java.time.LocalDateTime

/**
 * `budgets`: read by `BudgetRepositoryImpl.budgetFromDocument` (the listener), stored through
 * Room and written back by `budgetToFirestoreMap` with `set()`, stamped with the document's owner
 * as `updateBudget` does.
 */
internal object BudgetWireContract : WireContract {

    private val repository by lazy { BudgetRepositoryImpl(mockk(), mockk(), mockk(), mockk(), mockk()) }

    override val collection = "budgets"

    override val alwaysWrites = setOf(
        "id", "forMembers", "familyId", "category", "monthlyLimit", "currency", "alertThreshold", "isActive",
        "createdByFirebaseUid", "createdAt"
    )

    override val blankMeansAbsent = setOf("familyId")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        val budget = runCatching { repository.budgetFromDocument(document) }.getOrNull() ?: return null
        return mapOf(
            "id" to budget.id,
            "category" to budget.category.name,
            "monthlyLimit" to budget.monthlyLimit,
            "currency" to budget.currency,
            "alertThreshold" to budget.alertThreshold,
            "isActive" to budget.isActive,
            "forMembers" to FamilyMemberRef.store(budget.forMembers),
            "familyId" to budget.familyId
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        val owner = (document["createdByFirebaseUid"] as? String).orEmpty()
        return with(repository) {
            budgetToFirestoreMap(budgetFromDocument(document).toEntity().toDomain(), ownerUid = owner)
        }
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "BudgetRepositoryImpl.addBudget's document, scoped to one child.",
            document = repository.budgetToFirestoreMap(budget(), ownerUid = "uidA")
        )
    )

    private fun budget() = Budget(
        id = "budget-current-1",
        forMembers = listOf(FamilyMemberRef.Child("c1")),
        category = ExpenseCategory.EDUCATION,
        monthlyLimit = 3000.0,
        currency = "CZK",
        alertThreshold = 0.75,
        isActive = true,
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        familyId = "uidA__uidB"
    )
}
