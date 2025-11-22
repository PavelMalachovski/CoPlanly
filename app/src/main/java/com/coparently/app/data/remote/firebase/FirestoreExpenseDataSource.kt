package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for managing expenses in Firestore.
 */
@Singleton
class FirestoreExpenseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val expensesCollection = firestore.collection("expenses")

    /**
     * Gets all expenses as a Flow.
     */
    fun getAllExpenses(): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = expensesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val expenses = snapshot.documents.map { doc ->
                        doc.data?.plus("id" to doc.id) ?: emptyMap()
                    }
                    trySend(expenses)
                }
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Adds or updates an expense in Firestore.
     */
    suspend fun setExpense(expenseId: String, expenseData: Map<String, Any>) {
        expensesCollection.document(expenseId).set(expenseData).await()
    }

    /**
     * Deletes an expense from Firestore.
     */
    suspend fun deleteExpense(expenseId: String) {
        expensesCollection.document(expenseId).delete().await()
    }
}
