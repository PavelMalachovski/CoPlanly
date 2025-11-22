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
 * Data source for managing messages in Firestore.
 */
@Singleton
class FirestoreMessageDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val conversationsCollection = firestore.collection("conversations")
    private val messagesCollection = firestore.collection("messages")

    /**
     * Gets conversations for a user as a Flow.
     */
    fun getConversations(userId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = conversationsCollection
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val conversations = snapshot.documents.map { doc ->
                        doc.data?.plus("id" to doc.id) ?: emptyMap()
                    }
                    trySend(conversations)
                }
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Gets messages for a conversation as a Flow.
     */
    fun getMessages(conversationId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = messagesCollection
            .whereEqualTo("conversationId", conversationId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.map { doc ->
                        doc.data?.plus("id" to doc.id) ?: emptyMap()
                    }
                    trySend(messages)
                }
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Sends a message to Firestore.
     */
    suspend fun sendMessage(messageId: String, messageData: Map<String, Any>) {
        messagesCollection.document(messageId).set(messageData).await()

        // Update last message in conversation
        val conversationId = messageData["conversationId"] as String
        conversationsCollection.document(conversationId).update(
            mapOf(
                "lastMessage" to messageData,
                "updatedAt" to messageData["timestamp"]
            )
        ).await()
    }

    /**
     * Creates or updates a conversation in Firestore.
     */
    suspend fun setConversation(conversationId: String, conversationData: Map<String, Any>) {
        conversationsCollection.document(conversationId).set(conversationData).await()
    }

    /**
     * Marks messages as read.
     */
    suspend fun markAsRead(conversationId: String, userId: String) {
        // This would typically involve updating a "readBy" map or array in the conversation document
        // For simplicity, we'll just update the unread count logic in the repository
    }
}
