package com.coparently.app.utils

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

/**
 * Compose optimization utilities for improving performance and reducing recompositions.
 */
object ComposeOptimizations {

    /**
     * Stable data class for event display in Compose.
     * Using @Stable annotation helps Compose skip unnecessary recompositions.
     */
    @Stable
    data class EventDisplayData(
        val id: String,
        val title: String,
        val description: String,
        val startDateTime: LocalDateTime,
        val endDateTime: LocalDateTime?,
        val eventType: String,
        val parentOwner: String,
        val color: Color,
        val isRecurring: Boolean = false
    )

    /**
     * Creates a stable list that only recomposes when the keys of items change.
     * This is useful for lists where the content might change but the structure remains the same.
     *
     * @param items The list of items to make stable
     * @param keySelector Function to extract a key from each item
     * @return A stable copy of the list
     */
    @Composable
    fun <T, K> rememberStableList(
        items: List<T>,
        keySelector: (T) -> K
    ): List<T> {
        val keys = remember(items) {
            items.map(keySelector)
        }

        return remember(keys) {
            items.toList() // Create a stable copy
        }
    }

    /**
     * Extension function for LazyListScope to add items with optimized content type.
     * This helps Compose reuse compositions more efficiently.
     */
    inline fun <T> LazyListScope.optimizedItems(
        items: List<T>,
        noinline key: ((item: T) -> Any)? = null,
        crossinline contentType: (item: T) -> Any? = { it?.let { it::class.simpleName } },
        crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
    ) {
        items(
            count = items.size,
            key = if (key != null) { index -> key(items[index]) } else null,
            contentType = { index -> contentType(items[index]) }
        ) { index ->
            itemContent(items[index])
        }
    }
}
