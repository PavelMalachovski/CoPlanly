package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a message in the local Room database.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: LocalDateTime,
    val messageType: String, // Stored as string (TEXT, IMAGE, etc.)
    val attachmentsJson: String = "[]", // JSON array of URLs
    val isRead: Boolean = false,
    val replyToMessageId: String? = null,
    val syncedToFirestore: Boolean = false
)
