package com.coparently.app.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.ai.AiConsentDialog
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing

/**
 * "Suggest a reply", the first row of the templates sheet — or nothing at all when this build
 * does not offer the AI assist (design item 8).
 *
 * Says what it does and that the result is AI-generated. A tap asks for the consent the first
 * time ([AiConsentDialog]), then shows progress in the row; the draft arrives through
 * [onSuggested], which puts it into the composer for the parent to edit. A failure is worded in
 * the row and the row can be tapped again. Closing the sheet abandons the request.
 *
 * @param conversationId The thread the draft replies in
 * @param draftHint What the parent has typed so far
 * @param onSuggested Receives the draft; never sends it
 * @param viewModel The request's state
 */
@Composable
fun ReplySuggestionRow(
    conversationId: String,
    draftHint: String,
    onSuggested: (String) -> Unit,
    viewModel: ReplySuggestionViewModel = hiltViewModel()
) {
    if (!viewModel.available) return
    val state by viewModel.state.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    // The language the parent reads the app in — the per-app choice included — is the one the
    // draft is written in.
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()

    LaunchedEffect(suggestion) {
        suggestion?.let { text ->
            viewModel.consumeSuggestion()
            onSuggested(text)
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancel() }
    }

    val working = state == AiAssistState.Working
    val failure = (state as? AiAssistState.Failed)?.message
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSizes.Standard)
            )
        },
        headlineContent = { Text(stringResource(R.string.ai_reply_suggest_title)) },
        supportingContent = {
            when {
                working -> Text(stringResource(R.string.ai_reply_suggest_working))
                failure != null -> Text(text = failure.asString(), color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.ai_reply_suggest_subtitle))
            }
        },
        trailingContent = if (working) {
            { CircularProgressIndicator(modifier = Modifier.size(IconSizes.Standard)) }
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = !working) {
            viewModel.dismissFailure()
            viewModel.suggest(conversationId, locale, draftHint)
        }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S))

    if (state == AiAssistState.AskingConsent) {
        AiConsentDialog(onAgree = viewModel::agree, onCancel = viewModel::decline)
    }
}
