package com.coparently.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.payments.SettleUpPanel
import com.coparently.app.presentation.payments.SettleUpPanelActions
import com.coparently.app.presentation.payments.SettleUpPanelState
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.expenses_balance_settled
import com.coparently.app.resources.home_money_spent_in
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * From this font scale the month's label and its total stack instead of sharing a row (design
 * refresh item 15): side by side, a German label beside two currencies' totals left each a sliver.
 */
private const val STACK_FONT_SCALE = 1.3f

/**
 * Home's Money card (redesign, November 2026; board MoneySettleUp): what was spent this month and
 * who owes whom, which open Expenses, and under them the month's settle-up — asking the co-parent,
 * the payer's payment code and "I paid", the payee's "Money arrived", or settled — one
 * [SettleUpPanel] per currency with something to settle. It replaced the stat tiles; the unread
 * chat tile went with them, since the Chat tab carries its own badge.
 *
 * A month a confirmed payment settled reads as square, though its expenses do not change when one
 * parent pays the other (25 October 2026). Money is never cut off: every figure and sentence wraps.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param month The month the figures and the settle-up are for
 * @param spend The month's spend, per currency
 * @param balances The month's settle-up position, per currency
 * @param panels Where each currency's settle-up stands, in the order to draw them
 * @param coParentName The co-parent's name, never a role
 * @param payeeIban The account the co-parent is paid on, or null when they have added none
 * @param actionsFor What a panel's buttons do
 * @param onOpenExpenses Opens Expenses: a tap on the figures
 */
@Composable
@Suppress("LongParameterList") // the month's figures, its panels, who they are with and two actions
fun MoneyCard(
    month: YearMonth,
    spend: MonthSpend,
    balances: List<CurrencyBalance>,
    panels: List<SettleUpPanelState>,
    coParentName: String,
    payeeIban: String?,
    actionsFor: (SettleUpPanelState) -> SettleUpPanelActions,
    onOpenExpenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            MoneyFigures(
                month = month,
                spend = spend,
                caption = moneyCaption(balances, panels, coParentName),
                onOpenExpenses = onOpenExpenses
            )
            panels.forEach { panel ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettleUpPanel(
                    state = panel,
                    coParentName = coParentName,
                    payeeIban = payeeIban,
                    actions = actionsFor(panel),
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M)
                )
            }
        }
    }
}

/** "Spent in November" and its total, then who owes whom; a tap opens Expenses. */
@Composable
private fun MoneyFigures(month: YearMonth, spend: MonthSpend, caption: String, onOpenExpenses: () -> Unit) {
    val monthName = remember(month) { ReaderDateText.pattern(month, "LLLL") }
    val label = stringResource(Res.string.home_money_spent_in, monthName)
    val total = spend.byCurrency.joinToString("\n") { formatMoney(it.amount, it.currency) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenExpenses)
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
            if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(total, style = MaterialTheme.typography.titleMediumEmphasized)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = total,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        textAlign = TextAlign.End
                    )
                }
            }
            Text(text = caption, style = MaterialTheme.typography.bodyMedium)
        }
        AppIcon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSizes.Standard)
        )
    }
}

/**
 * Who owes whom, leaving out each currency a confirmed payment settled: "Bob owes you 620 CZK", or
 * "You are all square" once nothing is left open.
 */
@Composable
private fun moneyCaption(
    balances: List<CurrencyBalance>,
    panels: List<SettleUpPanelState>,
    coParentName: String
): String {
    val settledByPayment = panels.filterIsInstance<SettleUpPanelState.Settled>().map { it.currency }.toSet()
    val open = balances.filterNot { it.currency in settledByPayment }
    return if (SettleUpPosition.of(open).settled) {
        stringResource(Res.string.expenses_balance_settled)
    } else {
        balanceCaption(open, coParentName)
    }
}
