package com.coparently.app.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.domain.model.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    val conversation = conversations.find { it.id == conversationId }

    var showTemplates by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        viewModel.setConversationId(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MessagesList(
                messages = messages,
                currentUserId = currentUserId,
                modifier = Modifier.weight(1f)
            )

            MessageInput(
                onSendMessage = { content ->
                    viewModel.sendMessage(content)
                },
                onAttachClick = { showTemplates = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showTemplates) {
        MessageTemplatesBottomSheet(
            onTemplateSelected = { template ->
                // For now, just send the template content directly
                // In a real app, we might want to show a dialog to fill placeholders
                viewModel.sendTemplateMessage(template, template.content)
                showTemplates = false
            },
            onDismiss = { showTemplates = false }
        )
    }
}
