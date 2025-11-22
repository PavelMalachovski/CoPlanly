package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a conversation in the local Room database.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val participantsJson: String, // JSON array of user IDs
    val title: String,
    val lastMessageId: String? = null, // Reference to the last message
    val unreadCount: Int = 0,
    val createdAt: LocalDateTime,
    val syncedToFirestore: Boolean = false
)
