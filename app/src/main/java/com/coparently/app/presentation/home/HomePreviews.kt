package com.coparently.app.presentation.home

import androidx.compose.runtime.Composable
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.presentation.payments.SettleUpPanelActions
import com.coparently.app.presentation.payments.SettleUpPanelState
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import com.coparently.app.utils.previewParentNames
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/*
 * Home's previews. They stayed in the app when Home's screens moved to `:shared-ui` (MON-24 phase
 * 2, Task 11, slice 11g), as `CalendarPreviews.kt` did: the preview annotations and
 * `PreviewWrapper` are Android Studio's.
 */

@LightDarkPreviews
@Composable
private fun HandoverCardPreview() {
    PreviewWrapper {
        HandoverCard(
            info = HandoverInfo(
                date = LocalDate.now().plusDays(1).toKotlinLocalDate(),
                daysUntil = 1L,
                fromParent = "mom",
                toParent = "dad"
            ),
            parentNames = previewParentNames
        )
    }
}

@LightDarkPreviews
@Composable
private fun MoneyCardPreview() {
    PreviewWrapper {
        MoneyCard(
            month = YearMonth(year = 2026, month = 10),
            spend = MonthSpend(byCurrency = listOf(CurrencyAmount(currency = "CZK", amount = 4_250.0))),
            balances = emptyList(),
            panels = listOf(
                SettleUpPanelState.Pay(
                    month = YearMonth(year = 2026, month = 10),
                    currency = "CZK",
                    amountMinor = 62_000
                )
            ),
            coParentName = "Alice",
            payeeIban = "CZ6508000000192000145399",
            actionsFor = { SettleUpPanelActions(onAskToSettle = {}, onMarkPaid = {}, onConfirm = {}) },
            onOpenExpenses = {}
        )
    }
}
