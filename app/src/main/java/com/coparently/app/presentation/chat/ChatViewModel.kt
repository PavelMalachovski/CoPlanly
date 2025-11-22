package com.coparently.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageTemplate
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)

    private val _currentUserId = MutableStateFlow<String>("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUserId.value = user.id
                messageRepository.syncWithFirestore()
            }
        }
    }

    val conversations: StateFlow<List<Conversation>> = _currentUserId
        .combine(messageRepository.getConversations(_currentUserId.value)) { userId, conversations ->
            if (userId.isNotEmpty()) conversations else emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val messages: StateFlow<List<Message>> = _currentConversationId
        .combine(messageRepository.getMessages(_currentConversationId.value ?: "")) { conversationId, messages ->
            if (conversationId != null) messages else emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setConversationId(conversationId: String) {
        _currentConversationId.value = conversationId
        viewModelScope.launch {
            if (_currentUserId.value.isNotEmpty()) {
                messageRepository.markAsRead(conversationId, _currentUserId.value)
            }
        }
    }

    fun sendMessage(content: String, type: MessageType = MessageType.TEXT, attachments: List<String> = emptyList()) {
        val conversationId = _currentConversationId.value ?: return
        val userId = _currentUserId.value
        if (userId.isEmpty()) return

        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val senderName = user?.name ?: "Unknown"

            val message = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = userId,
                senderName = senderName,
                content = content,
                timestamp = LocalDateTime.now(),
                messageType = type,
                attachments = attachments
            )
            messageRepository.sendMessage(message)
        }
    }

    fun sendTemplateMessage(template: MessageTemplate, filledContent: String) {
        sendMessage(filledContent, MessageType.TEXT)
    }

    fun createConversation(otherUserId: String, title: String) {
        val userId = _currentUserId.value
        if (userId.isEmpty()) return

        viewModelScope.launch {
            val conversation = Conversation(
                id = UUID.randomUUID().toString(),
                participants = listOf(userId, otherUserId),
                title = title,
                createdAt = LocalDateTime.now()
            )
            messageRepository.createConversation(conversation)
            _currentConversationId.value = conversation.id
        }
    }
}
