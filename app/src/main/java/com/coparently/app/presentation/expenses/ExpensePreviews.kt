package com.coparently.app.presentation.expenses

import androidx.compose.runtime.Composable
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/*
 * The Expenses header's previews. They stayed in the app when the expense screens moved to
 * `:shared-ui` (MON-24 phase 2, Task 11, slice 11e), as `CalendarPreviews.kt` did: the preview
 * annotations and `PreviewWrapper` are Android Studio's.
 */

/** Two named parents, so the previews render the same shape a paired device does. */
private val previewParentNames = ParentNames(
    parents = Parents(
        me = NamedParent(uid = "u1", slot = "mom", name = "Olya"),
        coParent = NamedParent(uid = "u2", slot = "dad", name = "Pavel"),
        loaded = true
    ),
    youFallback = "You",
    coParentFallback = "Co-parent",
    unknownFallback = "Parent"
)

@LightDarkPreviews
@Composable
private fun ExpenseSummaryHeaderPairedPreview() {
    PreviewWrapper {
        ExpenseSummaryHeader(
            balance = ExpenseBalance(
                momPaid = 154.10,
                dadPaid = 94.40,
                total = 248.50,
                netForCurrentUser = 29.85,
                splitKnown = true
            ),
            currency = "USD",
            parentNames = previewParentNames,
            onSettleUp = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun ExpenseSummaryHeaderUnpairedPreview() {
    PreviewWrapper {
        ExpenseSummaryHeader(
            balance = ExpenseBalance(
                momPaid = 248.50,
                dadPaid = 0.0,
                total = 248.50,
                netForCurrentUser = 0.0,
                splitKnown = false
            ),
            currency = "USD",
            parentNames = previewParentNames,
            onSettleUp = {}
        )
    }
}
