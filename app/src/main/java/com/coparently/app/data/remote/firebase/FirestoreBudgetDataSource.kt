package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for managing budgets in Firestore.
 */
@Singleton
class FirestoreBudgetDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val budgetsCollection = firestore.collection("budgets")

    /**
     * Gets all budgets as a Flow.
     */
    fun getAllBudgets(): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = budgetsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val budgets = snapshot.documents.map { doc ->
                        doc.data?.plus("id" to doc.id) ?: emptyMap()
                    }
                    trySend(budgets)
                }
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Adds or updates a budget in Firestore.
     */
    suspend fun setBudget(budgetId: String, budgetData: Map<String, Any>) {
        budgetsCollection.document(budgetId).set(budgetData).await()
    }

    /**
     * Deletes a budget from Firestore.
     */
    suspend fun deleteBudget(budgetId: String) {
        budgetsCollection.document(budgetId).delete().await()
    }
}
