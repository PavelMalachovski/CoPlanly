package com.coparently.app.presentation.home

import androidx.compose.runtime.Composable
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.resources.Res
import com.coparently.app.resources.expenses_balance_owed_to_you_named
import com.coparently.app.resources.expenses_balance_you_owe_named
import com.coparently.app.resources.home_stat_owed_to_you
import com.coparently.app.resources.home_stat_settled
import com.coparently.app.resources.home_stat_you_owe
import com.coparently.app.utils.ReaderMoneyText
import org.jetbrains.compose.resources.stringResource

/*
 * How the month's money reads on Home's tile and the Family tab's money card. In `:shared-ui`
 * since MON-24 phase 2, slice 11e, which moved the Family tab; Home's screen calls them from the
 * app until it moves too.
 */

/**
 * Formats an amount in the reader's own conventions, in the currency the record was entered in.
 *
 * The locale and the currency are separate decisions and this needs both: the *currency* comes
 * from the expense (a Czech family may still record something in EUR), while the *formatting* —
 * decimal comma, thin space between thousands, symbol after the number — belongs to whoever is
 * reading. Pinned to `Locale.US`, this rendered `CZK 1,234.56` on the "this month" tile of a
 * Czech user's home screen, where `1 234,56 Kč` is what they expect on a figure two parents are
 * about to settle between them.
 *
 * Through the money seam, [ReaderMoneyText]: on Android the `NumberFormat` currency instance in the
 * default locale with the record's currency, the call this made before it moved.
 */
fun formatMoney(amount: Double, currencyCode: String): String = ReaderMoneyText.amount(amount, currencyCode)

/**
 * The settle-up line under the spend figure: "You are owed 1 416,00 CZK, 58,00 €", "You owe
 * 29.85", or "All settled".
 *
 * Every currency with an open balance is named ([SettleUpPosition]); the app does no FX
 * conversion, so they are listed, never summed. A month that owes one way in one currency and
 * the other way in another gets both sentences, one per line — there is no honest single one.
 */
@Composable
fun balanceCaption(balances: List<CurrencyBalance>, coParentName: String? = null): String {
    val position = SettleUpPosition.of(balances)
    if (position.settled) return stringResource(Res.string.home_stat_settled)
    val amounts: (List<CurrencyAmount>) -> String = { list ->
        list.joinToString(", ") { formatMoney(it.amount, it.currency) }
    }
    // With a name, the sentence says who owes whom, as the Expenses balance does; the Family tab's
    // card passes one, Home's tile keeps its shorter line and arrow.
    val owedToYou: @Composable (String) -> String = { sum ->
        coParentName?.let { stringResource(Res.string.expenses_balance_owed_to_you_named, it, sum) }
            ?: stringResource(Res.string.home_stat_owed_to_you, sum)
    }
    val youOwe: @Composable (String) -> String = { sum ->
        coParentName?.let { stringResource(Res.string.expenses_balance_you_owe_named, it, sum) }
            ?: stringResource(Res.string.home_stat_you_owe, sum)
    }
    return listOfNotNull(
        position.owedToYou.takeIf { it.isNotEmpty() }?.let { owedToYou(amounts(it)) },
        position.youOwe.takeIf { it.isNotEmpty() }?.let { youOwe(amounts(it)) }
    ).joinToString("\n")
}
