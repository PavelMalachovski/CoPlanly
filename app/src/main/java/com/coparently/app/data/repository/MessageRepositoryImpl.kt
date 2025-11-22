package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.entity.ConversationEntity
import com.coparently.app.data.local.entity.MessageEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.repository.MessageRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val firestoreMessageDataSource: FirestoreMessageDataSource
) : MessageRepository {

    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override fun getConversations(userId: String): Flow<List<Conversation>> {
        return messageDao.getConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessages(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getConversationById(id: String): Conversation? {
        return messageDao.getConversationById(id)?.toDomain()
    }

    override suspend fun sendMessage(message: Message) {
        val entity = message.toEntity()
        messageDao.insertMessage(entity)

        // Sync to Firestore
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val messageData = mapOf(
                "id" to message.id,
                "conversationId" to message.conversationId,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "content" to message.content,
                "timestamp" to message.timestamp.format(dateFormatter),
                "messageType" to message.messageType.name,
                "attachments" to message.attachments,
                "isRead" to message.isRead,
                "replyToMessageId" to (message.replyToMessageId ?: "")
            )
            firestoreMessageDataSource.sendMessage(message.id, messageData)

            // Mark as synced
            val syncedMessage = message.copy(syncedToFirestore = true)
            messageDao.insertMessage(syncedMessage.toEntity())
        }
    }

    override suspend fun markAsRead(conversationId: String, userId: String) {
        messageDao.markConversationAsRead(conversationId)

        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            firestoreMessageDataSource.markAsRead(conversationId, userId)
        }
    }

    override suspend fun createConversation(conversation: Conversation) {
        val entity = conversation.toEntity()
        messageDao.insertConversation(entity)

        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val conversationData = mapOf(
                "id" to conversation.id,
                "participants" to conversation.participants,
                "title" to conversation.title,
                "unreadCount" to conversation.unreadCount,
                "createdAt" to conversation.createdAt.format(dateFormatter)
            )
            firestoreMessageDataSource.setConversation(conversation.id, conversationData)

            val syncedConversation = conversation.copy(syncedToFirestore = true)
            messageDao.insertConversation(syncedConversation.toEntity())
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
        // Note: Deleting from Firestore is not implemented in this version for safety
    }

    override suspend fun syncWithFirestore() {
        val firebaseUser = firebaseAuthService.getCurrentUser() ?: return

        // Sync conversations
        firestoreMessageDataSource.getConversations(firebaseUser.uid).collect { conversations ->
            conversations.forEach { data ->
                val conversation = Conversation(
                    id = data["id"] as String,
                    participants = (data["participants"] as? List<String>) ?: emptyList(),
                    title = data["title"] as String,
                    unreadCount = (data["unreadCount"] as? Long)?.toInt() ?: 0,
                    createdAt = LocalDateTime.parse(data["createdAt"] as String, dateFormatter),
                    syncedToFirestore = true
                )
                messageDao.insertConversation(conversation.toEntity())

                // Sync messages for this conversation
                syncMessagesForConversation(conversation.id)
            }
        }
    }

    private suspend fun syncMessagesForConversation(conversationId: String) {
        firestoreMessageDataSource.getMessages(conversationId).collect { messages ->
            messages.forEach { data ->
                val message = Message(
                    id = data["id"] as String,
                    conversationId = data["conversationId"] as String,
                    senderId = data["senderId"] as String,
                    senderName = data["senderName"] as String,
                    content = data["content"] as String,
                    timestamp = LocalDateTime.parse(data["timestamp"] as String, dateFormatter),
                    messageType = MessageType.valueOf(data["messageType"] as String),
                    attachments = (data["attachments"] as? List<String>) ?: emptyList(),
                    isRead = (data["isRead"] as? Boolean) ?: false,
                    replyToMessageId = data["replyToMessageId"] as? String,
                    syncedToFirestore = true
                )
                messageDao.insertMessage(message.toEntity())
            }
        }
    }

    private fun ConversationEntity.toDomain(): Conversation {
        val participantsListType = object : TypeToken<List<String>>() {}.type
        val participants: List<String> = gson.fromJson(participantsJson, participantsListType)

        return Conversation(
            id = id,
            participants = participants,
            title = title,
            unreadCount = unreadCount,
            createdAt = createdAt,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Conversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            participantsJson = gson.toJson(participants),
            title = title,
            unreadCount = unreadCount,
            createdAt = createdAt,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun MessageEntity.toDomain(): Message {
        val attachmentsListType = object : TypeToken<List<String>>() {}.type
        val attachments: List<String> = gson.fromJson(attachmentsJson, attachmentsListType)

        return Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            content = content,
            timestamp = timestamp,
            messageType = MessageType.valueOf(messageType),
            attachments = attachments,
            isRead = isRead,
            replyToMessageId = replyToMessageId,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Message.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            content = content,
            timestamp = timestamp,
            messageType = messageType.name,
            attachmentsJson = gson.toJson(attachments),
            isRead = isRead,
            replyToMessageId = replyToMessageId,
            syncedToFirestore = syncedToFirestore
        )
    }
}
