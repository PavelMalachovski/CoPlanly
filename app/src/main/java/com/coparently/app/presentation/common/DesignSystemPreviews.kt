package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

// Android Studio's previews of the design system's primitives (`DesignSystem.kt`, in `:shared-ui`
// since MON-24 phase 2). They stay in the app beside `PreviewWrapper`, which they render through;
// Android Studio draws a preview from an Android source set.

@LightDarkPreviews
@Composable
private fun EmptyStatePreview() {
    PreviewWrapper {
        EmptyState(
            icon = Icons.Default.Contacts,
            title = "No contacts yet",
            description = "Save the school, the doctor or a relative to reach them in one tap.",
            actionLabel = "Add a contact",
            onAction = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@LightDarkPreviews
@Composable
private fun SectionGroupPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(Spacing.L)) {
            GroupLabel("Family")
            SectionGroup {
                SectionRow(
                    icon = Icons.Default.Contacts,
                    title = "Contacts",
                    supporting = "3 saved",
                    onClick = {}
                )
                Divider()
                SectionRow(
                    icon = Icons.Default.Balance,
                    title = "Expense split",
                    supporting = "50 / 50",
                    trailing = {
                        PillChip(label = "Agreed", leadingDot = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}
