package com.coparently.app.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.R
import com.coparently.app.domain.model.DefaultMessageTemplates
import com.coparently.app.domain.model.MessageTemplate
import com.coparently.app.presentation.theme.Spacing

/**
 * The message templates, grouped by category; a tap prepares the message in the composer.
 *
 * @param onTemplateSelected Receives the chosen template
 * @param onDismiss Closes the sheet
 * @param header Drawn above the templates when given — the "Suggest a reply" row, which draws
 *   nothing while the AI assist is off
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTemplatesBottomSheet(
    onTemplateSelected: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null
) {
    val templates by remember {
        mutableStateOf(DefaultMessageTemplates.getAll())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.L)
        ) {
            Text(
                text = stringResource(R.string.chat_templates_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.L)
            )

            header?.invoke()

            LazyColumn {
                items(templates.groupBy { it.category }.toList()) { (category, categoryTemplates) ->
                    Text(
                        text = stringResource(category.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = Spacing.S)
                            .semantics { heading() }
                    )

                    categoryTemplates.forEach { template ->
                        TemplateItem(
                            template = template,
                            onClick = { onTemplateSelected(template) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S))
                }
            }
        }
    }
}

@Composable
fun TemplateItem(
    template: MessageTemplate,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(template.titleRes)) },
        supportingContent = {
            Text(
                text = stringResource(template.contentRes),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
