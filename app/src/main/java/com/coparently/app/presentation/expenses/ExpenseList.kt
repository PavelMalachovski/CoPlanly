package com.coparently.app.presentation.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.coparently.app.R
import com.coparently.app.domain.model.Expense
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExpenseList(
    expenses: List<Expense>,
    onExpenseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Receipt being viewed full-screen; transient UI state, deliberately local.
    var viewedReceiptUrl by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
    ) {
        items(expenses) { expense ->
            ExpenseItem(
                expense = expense,
                onClick = { onExpenseClick(expense.id) },
                onReceiptClick = { url -> viewedReceiptUrl = url }
            )
            HorizontalDivider()
        }
    }

    viewedReceiptUrl?.let { url ->
        ReceiptViewerDialog(
            receiptUrl = url,
            onDismiss = { viewedReceiptUrl = null }
        )
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    onClick: () -> Unit,
    onReceiptClick: (String) -> Unit = {}
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.currency = java.util.Currency.getInstance(expense.currency)

    ListItem(
        headlineContent = { Text(expense.title) },
        supportingContent = {
            Column {
                Text(expense.category.displayName)
                Text(
                    text = expense.date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = expense.receiptUrl?.let { url ->
            {
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.expenses_receipt_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onReceiptClick(url) }
                )
            }
        },
        trailingContent = {
            Text(
                text = format.format(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Full-width receipt photo viewer; tap anywhere on the image or outside to close.
 */
@Composable
private fun ReceiptViewerDialog(
    receiptUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AsyncImage(
            model = receiptUrl,
            contentDescription = stringResource(R.string.expenses_receipt_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onDismiss)
        )
    }
}
