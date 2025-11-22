package com.coparently.app.domain.repository

import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing messages and conversations.
 * Part of the domain layer in Clean Architecture.
 */
interface MessageRepository {
    /**
     * Gets all conversations for a user as a Flow.
     */
    fun getConversations(userId: String): Flow<List<Conversation>>

    /**
     * Gets all messages for a specific conversation as a Flow.
     */
    fun getMessages(conversationId: String): Flow<List<Message>>

    /**
     * Gets a conversation by ID.
     */
    suspend fun getConversationById(id: String): Conversation?

    /**
     * Sends a new message.
     */
    suspend fun sendMessage(message: Message)

    /**
     * Marks all messages in a conversation as read for a specific user.
     */
    suspend fun markAsRead(conversationId: String, userId: String)

    /**
     * Creates a new conversation.
     */
    suspend fun createConversation(conversation: Conversation)

    /**
     * Deletes a message.
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Syncs messages with Firestore.
     */
    suspend fun syncWithFirestore()
}
