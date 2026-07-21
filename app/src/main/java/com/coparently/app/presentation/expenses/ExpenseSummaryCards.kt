package com.coparently.app.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.model.ExpenseSummary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ExpenseSummaryCards(
    summary: ExpenseSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.expenses_summary_this_month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Total Card
            item {
                SummaryCard(
                    title = stringResource(R.string.expenses_summary_total),
                    amount = summary.totalAmount,
                    currency = summary.currency,
                    color = MaterialTheme.colorScheme.primaryContainer
                )
            }

            // Category Cards
            items(summary.byCategory.entries.toList().sortedByDescending { it.value }) { (category, amount) ->
                SummaryCard(
                    title = category.displayName,
                    amount = amount,
                    currency = summary.currency,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: Double,
    currency: String,
    color: androidx.compose.ui.graphics.Color
) {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.currency = java.util.Currency.getInstance(currency)

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier
            .width(140.dp)
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = format.format(amount),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
