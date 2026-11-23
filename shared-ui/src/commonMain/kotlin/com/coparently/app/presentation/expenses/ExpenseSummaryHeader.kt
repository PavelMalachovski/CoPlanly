package com.coparently.app.presentation.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.payments.SettleUpPanel
import com.coparently.app.presentation.payments.SettleUpPanelActions
import com.coparently.app.presentation.payments.SettleUpPanelState
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelLargeEmphasized
import com.coparently.app.presentation.theme.titleSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.expenses_balance_owed_to_you
import com.coparently.app.resources.expenses_balance_owed_to_you_named
import com.coparently.app.resources.expenses_balance_settled
import com.coparently.app.resources.expenses_balance_you_owe
import com.coparently.app.resources.expenses_balance_you_owe_named
import com.coparently.app.resources.expenses_collapsed_totals
import com.coparently.app.resources.expenses_count
import com.coparently.app.resources.expenses_month_and_count
import com.coparently.app.resources.expenses_next_month
import com.coparently.app.resources.expenses_paid_by
import com.coparently.app.resources.expenses_prev_month
import com.coparently.app.resources.expenses_shared_spend
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Height of the who-paid split bar. */
private val SPLIT_BAR_HEIGHT = 8.dp

/** Below this the balance is treated as settled — sub-cent drift is not a debt. */
private const val SETTLED_EPSILON = 0.01

/** Tint strength of the strip behind the settle-up row. */
private const val BALANCE_STRIP_ALPHA = 0.12f

/**
 * From this font scale the card's side-by-side pairs stack: the month's total over its label, and
 * the two parents' amounts under the split bar. Side by side at 150 % German broke both
 * ("gemeinsam|e", "820,0|0 CZK").
 */
private const val STACK_FONT_SCALE = 1.3f

/**
 * Month header for the Expenses screen: which month, total spend, who paid what, and who owes
 * whom — in one card.
 *
 * Replaces a horizontal row of category cards that carried no per-parent semantics at all — in
 * a two-household product the money screen never answered the question co-parents actually have,
 * which is "are we square?". The pink/blue split bar reuses the calendar's colour language
 * rather than inventing a second one.
 *
 * The August 2026 refresh folded the standalone month navigator into this card: the screen used
 * to stack an app bar, a month navigator and this summary — three headers before the first
 * expense row, leaving the list about 40% of the screen. [monthNavigation] supplies the
 * switcher; pass null on the second and subsequent cards of a mixed-currency month, so one
 * switcher governs them all.
 *
 * While the two parents cannot be told apart the split bar and balance row are hidden: with one
 * parent on record a 100%-pink bar and a zero balance would be decoration pretending to be data.
 * That covers being unpaired *and* a pair whose two parents still hold the same slot.
 *
 * The two amounts under the bar are labelled by name — "Olya: $154.10" — rather than by a role
 * word. The name is the label: the row's two halves are already positioned and coloured per
 * parent, so a repeated "Paid by" on both sides would distinguish nothing. Its meaning rests on
 * sitting directly under the [SplitBar]; keep it there.
 *
 * @param balance The selected month's paid/owed figures for one currency
 * @param currency ISO currency code for formatting
 * @param parentNames Resolves a slot to that parent's name
 * @param modifier Modifier for the card
 * @param monthNavigation Month switcher to render at the top of the card, or null for none
 * @param settleUp Where the month's settle-up stands in this currency and what this parent can do
 *   about it ([SettleUpPanel]), or null for none
 */
@Composable
@Suppress("LongParameterList") // one card, one parameter per thing it displays
fun ExpenseSummaryHeader(
    balance: ExpenseBalance,
    currency: String,
    parentNames: ParentNames,
    modifier: Modifier = Modifier,
    monthNavigation: MonthNavigation? = null,
    settleUp: ExpenseSettleUp? = null
) {
    val format = remember(currency) { MoneyFormat(currency) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.L)) {
            if (monthNavigation != null) {
                MonthSwitcherBar(
                    navigation = monthNavigation,
                    modifier = Modifier.padding(bottom = Spacing.M)
                )
            }

            TotalWithLabel(total = format.format(balance.total))

            if (balance.splitKnown) {
                SplitBar(
                    momShare = balance.momShareOfPaid,
                    modifier = Modifier.padding(top = Spacing.M)
                )
                PaidByRow(
                    momLine = stringResource(
                        Res.string.expenses_paid_by,
                        parentNames.labelOrYou("mom"),
                        format.format(balance.momPaid)
                    ),
                    dadLine = stringResource(
                        Res.string.expenses_paid_by,
                        parentNames.labelOrYou("dad"),
                        format.format(balance.dadPaid)
                    )
                )

                BalanceRow(
                    balance = balance,
                    format = format,
                    coParentName = parentNames.parents.coParent?.name?.takeIf { it.isNotBlank() },
                    modifier = Modifier.padding(top = Spacing.M),
                    settleUp = settleUp?.takeIf { it.state.currency == currency }
                )
            }
        }
    }
}

/**
 * Which month the summary is showing and how to page it.
 *
 * @property label Month name and year, already formatted and capitalised
 * @property expenseCount Number of expenses in that month
 * @property onPrevious Pages one month back
 * @property onNext Pages one month forward
 */
data class MonthNavigation(
    val label: String,
    val expenseCount: Int,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit
)

/**
 * Who paid what, by name, directly under the split bar: side by side, or one above the other
 * from [STACK_FONT_SCALE] (design refresh item 15).
 *
 * Side by side, each half gets an equal share of the row and wraps inside it: a long name on the
 * start side would otherwise push the other parent's figure off the end. It used to ellipsise,
 * which at 150 % cut the amount itself ("Olya: 3.120,00 C…"). Wrapping alone stops working at
 * larger sizes, because the currency formatter joins an amount to its code with a no-break space:
 * once "1.480,00 CZK" is wider than half the card, it breaks inside the code ("CZ|K", German at
 * 2.0×) instead of at the space. Stacked, each line has the whole width.
 *
 * @param momLine Slot 1's name and amount, formatted
 * @param dadLine Slot 2's name and amount, formatted
 */
@Composable
private fun PaidByRow(momLine: String, dadLine: String) {
    val style = MaterialTheme.typography.labelMedium
    val momColor = ParentColors.text("mom")
    val dadColor = ParentColors.text("dad")
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.S)
        ) {
            Text(text = momLine, style = style, color = momColor)
            Text(text = dadLine, style = style, color = dadColor)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.S),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = momLine, style = style, color = momColor, modifier = Modifier.weight(1f))
            Text(
                text = dadLine,
                style = style,
                color = dadColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The month switcher: back/forward around "August 2026 · 5 expenses".
 *
 * Normally rendered inside the summary card. `ExpenseScreen` also uses it standalone for a
 * month with no expenses, which has no summary card to sit in but still has to be pageable, above
 * the analytics, which leave the cards to the list, and inside [CollapsedMonthSummary].
 *
 * @param navigation Month being shown and how to page it
 * @param modifier Modifier for the row
 */
@Composable
internal fun MonthSwitcherBar(navigation: MonthNavigation, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = navigation.onPrevious, modifier = Modifier.size(32.dp)) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.expenses_prev_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(
                Res.string.expenses_month_and_count,
                navigation.label,
                pluralStringResource(
                    Res.plurals.expenses_count,
                    navigation.expenseCount,
                    navigation.expenseCount
                )
            ),
            style = MaterialTheme.typography.labelLargeEmphasized,
            textAlign = TextAlign.Center,
            // Wraps rather than cutting the count ("Mai 2026 · 5 Ausgab…" in German at 2.0x).
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = navigation.onNext, modifier = Modifier.size(32.dp)) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.expenses_next_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The month's summary folded to one line: which month, the pager, and what was spent in each
 * currency (docs/AUDIT-2026-10-design.md D-6). `ExpenseScreen` pins it over the list once the
 * full cards have scrolled away, in the cards' own colour and hanging from the top edge, so it
 * reads as the same card folded up rather than as a new bar.
 *
 * The totals wrap rather than end in an ellipsis (design refresh item 15): two currencies at a
 * large font size take a second line, never half an amount.
 *
 * @param navigation The month being shown and how to page it, the same one the cards use
 * @param totals This month's total in each currency, already formatted
 * @param modifier Modifier for the bar
 */
@Composable
fun CollapsedMonthSummary(
    navigation: MonthNavigation,
    totals: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(start = Spacing.M, end = Spacing.M, bottom = Spacing.S)) {
            MonthSwitcherBar(navigation = navigation)
            Text(
                text = stringResource(
                    Res.string.expenses_collapsed_totals,
                    // A no-break space before the dot keeps it with the amount it follows, so
                    // a wrapped second line starts with an amount, never with "·".
                    totals.joinToString(separator = "\u00A0· ")
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Travel required before a drag over the month header commits to a month change. */
private val MONTH_SWIPE_THRESHOLD = 56.dp

/**
 * Horizontal swipe over the month header, resolved on release.
 *
 * Deliberately not a pager: the month's content is a summary card plus a list that owns its own
 * horizontal gesture, so this reads the drag and calls the same two callbacks the chevrons do.
 *
 * @param navigation The same [MonthNavigation] the chevrons use, so the two can never disagree
 */
@Composable
internal fun Modifier.monthSwipe(navigation: MonthNavigation): Modifier {
    val thresholdPx = with(LocalDensity.current) { MONTH_SWIPE_THRESHOLD.toPx() }
    return this.pointerInput(navigation.label) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onDragEnd = {
                when (MonthSwipe.resolve(drag, thresholdPx)) {
                    MonthStep.PREVIOUS -> navigation.onPrevious()
                    MonthStep.NEXT -> navigation.onNext()
                    MonthStep.NONE -> Unit
                }
            },
            onDragCancel = { drag = 0f },
            onHorizontalDrag = { change, amount ->
                drag += amount
                change.consume()
            }
        )
    }
}

/**
 * The month's total with its "shared spend" label: beside it, or under it from
 * [STACK_FONT_SCALE]. At 150 % the label beside a German total was squeezed into a sliver
 * that broke "gemeinsame" in the middle of the word.
 */
@Composable
private fun TotalWithLabel(total: String) {
    val totalText: @Composable () -> Unit = {
        Text(
            text = total,
            style = MaterialTheme.typography.headlineMedium
        )
    }
    val label = stringResource(Res.string.expenses_shared_spend)
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column {
            totalText()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            totalText()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.M, bottom = Spacing.XS)
            )
        }
    }
}

/** Proportional bar, in each parent's own colour, showing what share of the month each fronted. */
@Composable
private fun SplitBar(momShare: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SPLIT_BAR_HEIGHT)
            .clip(CoPlanlyCorners.Tag)
    ) {
        if (momShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(momShare)
                    .fillMaxHeight()
                    .background(ParentColors.fill("mom"))
            )
        }
        if (momShare < 1f) {
            Box(
                modifier = Modifier
                    .weight(1f - momShare)
                    .fillMaxHeight()
                    .background(ParentColors.fill("dad"))
            )
        }
    }
}

/**
 * "Bob owes you $29.85" on its own tinted strip, and under it what this parent can do about the
 * month ([SettleUpPanel]): ask the co-parent to settle up, pay them with the code, or say the money
 * arrived (redesign, November 2026: settle up left the chat for Money).
 *
 * The net figure carries no slot, but in a family of two the other side of it is the co-parent on
 * screen, so the line names them (new-user review, October 2026: "your co-parent" read as a form
 * letter). With no name known it falls back to "your co-parent". The named lines are worded so the
 * name stays in the nominative in every locale ("Bob: вы должны …"), never declined.
 *
 * A confirmed payment reads as square, as Home's Money card does, though the expenses themselves
 * do not change when one parent pays the other (25 October 2026).
 */
@Composable
private fun BalanceRow(
    balance: ExpenseBalance,
    format: MoneyFormat,
    coParentName: String?,
    modifier: Modifier = Modifier,
    settleUp: ExpenseSettleUp? = null
) {
    val net = balance.netForCurrentUser
    val amount = format.format(abs(net))
    val settled = abs(net) < SETTLED_EPSILON || settleUp?.state is SettleUpPanelState.Settled

    val label = when {
        settled -> stringResource(Res.string.expenses_balance_settled)
        net > 0 && coParentName != null ->
            stringResource(Res.string.expenses_balance_owed_to_you_named, coParentName, amount)
        net > 0 -> stringResource(Res.string.expenses_balance_owed_to_you, amount)
        coParentName != null -> stringResource(Res.string.expenses_balance_you_owe_named, coParentName, amount)
        else -> stringResource(Res.string.expenses_balance_you_owe, amount)
    }
    // Settled is a neutral fact, being owed is good news, owing is a nudge — but never an
    // error: owing your co-parent for half the school shoes is not a failure state.
    val accent = when {
        settled -> MaterialTheme.colorScheme.outline
        net > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        BalanceStrip(label = label, accent = accent)
        settleUp?.let {
            SettleUpPanel(
                state = it.state,
                coParentName = coParentName ?: it.coParentFallback,
                payeeIban = it.payeeIban,
                actions = it.actions
            )
        }
    }
}

/**
 * Where a month's settle-up stands in one currency, for [ExpenseSummaryHeader], and what its
 * buttons do — the panel Home's Money card draws too.
 *
 * @property coParentFallback The name a sentence uses while the co-parent's is not known
 */
class ExpenseSettleUp(
    val state: SettleUpPanelState,
    val payeeIban: String?,
    val actions: SettleUpPanelActions,
    val coParentFallback: String
)

/** The tinted strip [BalanceRow] draws: the sentence with its icon. */
@Composable
private fun BalanceStrip(label: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = BALANCE_STRIP_ALPHA))
            .padding(horizontal = Spacing.M, vertical = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        AppIcon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(IconSizes.Small)
        )
        // Wraps instead of ellipsising: the amount comes last in every language, so a one-line
        // cap cut exactly the figure this row exists to show — "Your co-parent owes yo…" in
        // English at the default size (docs/AUDIT-2026-10-design.md D-1).
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmallEmphasized,
            modifier = Modifier.weight(1f)
        )
    }
}
