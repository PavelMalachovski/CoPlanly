package com.coparently.app.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ai.EventSuggestion
import com.coparently.app.domain.model.ai.SuggestionCategory
import kotlin.math.roundToInt

/**
 * Screen displaying AI-generated event suggestions
 * Day 2 - Feature 2.2: Smart event suggestions
 */
@Composable
fun EventSuggestionsScreen(
    suggestions: List<EventSuggestion>,
    onSuggestionAccepted: (EventSuggestion) -> Unit,
    onSuggestionRejected: (EventSuggestion) -> Unit,
    onSuggestionModified: (EventSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                onAccept = { onSuggestionAccepted(suggestion) },
                onReject = { onSuggestionRejected(suggestion) },
                onModify = { onSuggestionModified(suggestion) }
            )
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: EventSuggestion,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onModify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Confidence indicator
                    LinearProgressIndicator(
                        progress = { suggestion.confidence.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(4.dp)
                    )

                    Text(
                        text = "${(suggestion.confidence * 100).roundToInt()}% match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Category chip
                SuggestionCategoryChip(suggestion.category)
            }

            // Reasoning
            Text(
                text = suggestion.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Not interested")
                }

                OutlinedButton(
                    onClick = onModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Modify")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Event")
                }
            }
        }
    }
}

@Composable
fun SuggestionCategoryChip(
    category: SuggestionCategory,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = when (category) {
                    SuggestionCategory.RECURRING -> "Recurring"
                    SuggestionCategory.SEASONAL -> "Seasonal"
                    SuggestionCategory.AGE_APPROPRIATE -> "Age-based"
                    SuggestionCategory.LOCATION_BASED -> "Location"
                    SuggestionCategory.WEATHER_BASED -> "Weather"
                },
                style = MaterialTheme.typography.labelSmall
            )
        },
        modifier = modifier
    )
}
