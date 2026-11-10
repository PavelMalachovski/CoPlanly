package com.coparently.app.data.remote.firebase

import com.coparently.app.data.sync.Tombstone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Data source for managing expenses in Firestore.
 */
class FirestoreExpenseDataSource(
    private val firestore: FirestoreClient
) {
    private val expensesCollection = "expenses"

    /**
     * Gets the expenses of one co-parenting relationship as a Flow.
     *
     * **Filtered on `familyId`, and the shape is load-bearing.** Firestore validates a *query*
     * by checking whether its structure guarantees every possible result satisfies the security
     * rule — it does not run the rule over the documents and drop the failures (CLAUDE.md item
     * 12). The `expenses` read rule is keyed on membership of the record's own family, so this
     * is the only filter it accepts.
     *
     * It replaces `whereIn("createdByFirebaseUid", [me, partner])`, which was not merely a
     * different spelling: a person with two co-parents has two families, and a filter on the
     * *author* returns both of them in one list. Worse, while any branch of the rule still
     * mentioned `isPartnerOf(createdByFirebaseUid)`, that query satisfied the rule structurally
     * and Firestore served it — which is how the leak survived a rule that looked closed.
     * Proved against the emulator in `firestore-tests/rules/family-isolation.test.js`.
     *
     * @param familyId `FamilyKey.of(myUid, partnerUid)` for the family being read. Empty or
     *   null closes the flow without a query: an unpaired account has no shared expenses to
     *   fetch, and an unfiltered read would be denied outright.
     */
    fun getAllExpenses(familyId: String?): Flow<List<Map<String, Any>>> {
        if (familyId.isNullOrEmpty()) return emptyFlow()
        return firestore.observe(
            DocumentQuery(expensesCollection)
                .whereEqualTo("familyId", familyId)
                .orderBy("date", descending = true)
        ).map { documents ->
            documents.map { doc -> doc.data?.plus("id" to doc.id)?.asFieldMap() ?: emptyMap() }
        }
    }

    /**
     * Adds or updates an expense in Firestore.
     */
    suspend fun setExpense(expenseId: String, expenseData: Map<String, Any>) {
        firestore.set(DocumentPath(expensesCollection, expenseId), expenseData)
    }

    /**
     * Fetches a single expense document by id, or `null` if it doesn't exist.
     *
     * Used by [com.coparently.app.data.repository.ExpenseRepositoryImpl.updateExpense] to read
     * back the existing `createdByFirebaseUid` before writing an update: `firestore.rules`
     * requires that field to stay unchanged on update, so the write path must know its current
     * value rather than re-stamping it with whichever parent happens to be editing.
     */
    suspend fun getExpense(expenseId: String): Map<String, Any>? {
        val snapshot = firestore.get(DocumentPath(expensesCollection, expenseId))
        return if (snapshot.exists) snapshot.data?.asFieldMap() else null
    }

    /**
     * Marks an expense deleted, leaving the document in place so the co-parent can be told.
     *
     * Same shape and the same reasoning as [FirestoreEventDataSource.tombstoneEvent], including
     * that a missing document is a success: there is no remote copy for anyone to be holding.
     *
     * One difference is worth naming. The `expenses` update rule is creator-only (an owner
     * decision: a co-parent must not rewrite the other's recorded expense), so a co-parent's
     * tombstone is refused here exactly as their hard delete was. That is unchanged behaviour,
     * not a regression — what changes is that the *creator's* delete now reaches the co-parent's
     * phone instead of only their own.
     */
    suspend fun tombstoneExpense(expenseId: String, deletedAtMillis: Long, deletedBy: String): Result<Unit> {
        return try {
            firestore.update(DocumentPath(expensesCollection, expenseId), Tombstone.fields(deletedAtMillis, deletedBy))
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (firestore.errorCodeOf(e) == FirestoreErrorCode.NOT_FOUND) {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    // `deleteExpense` — a bare `document().delete()` — was removed with CQ-3. It had exactly one
    // caller, the delete path, and removing the document is what made a deletion undeliverable:
    // there was nothing left for the co-parent's sync to read. `tombstoneExpense` replaced it,
    // and the documents are removed for good by the `sweepDeletedDocuments` schedule once the
    // retention window has passed. `FirestoreEventDataSource.deleteEvent` survives only because
    // it still has a caller that is not a deletion — an event turned private has to leave
    // Firestore with no trace at all.
}
