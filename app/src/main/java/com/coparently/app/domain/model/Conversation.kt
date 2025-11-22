package com.coparently.app.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing a conversation between parents.
 * This is the clean architecture model used in the domain layer.
 *
 * @property id Unique identifier for the conversation
 * @property participants List of Firebase UIDs of participants
 * @property title Title/name of the conversation
 * @property lastMessage The most recent message in the conversation
 * @property unreadCount Number of unread messages for the current user
 * @property createdAt When the conversation was created
 * @property syncedToFirestore Whether the conversation has been synced to Firestore
 */
data class Conversation(
    val id: String,
    val participants: List<String>,
    val title: String,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val syncedToFirestore: Boolean = false
)
