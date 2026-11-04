package com.coparently.app.presentation.changerequests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.presentation.common.ParentNames

/**
 * "Zuzana pays 35 %, Pavel pays 65 %": a split with each share beside the name of the parent who
 * pays it, as the Expenses banners word it — a bare "35 / 65" left both parents guessing which
 * number was theirs (tester report, October 2026). The inbox's split card and Home's pop-up say it
 * the same way.
 *
 * The percent sign is an argument, `expenses_percent_sign`, never a literal `%%` beside the
 * arguments, which Compose Multiplatform's reader would print as written (CLAUDE.md
 * "Localization"; MON-24 phase 2, slice 11e): "70 %" reads the same through both readers.
 *
 * @param ratio The split; its stored share is slot 1's, and slot 2 pays the rest.
 * @param parentNames Names each slot — never "Mom" or "Dad".
 */
@Composable
internal fun splitSharesText(ratio: SplitRatio, parentNames: ParentNames): String {
    val percent = stringResource(R.string.expenses_percent_sign)
    return when {
        // The reader's own share is "you pay" (UX audit H4), in the slot order the split bar draws.
        parentNames.isReader(ContactWindow.SLOT_ONE) -> stringResource(
            R.string.expenses_split_shares_you_first,
            ratio.momPercent,
            parentNames.labelFor(ContactWindow.SLOT_TWO),
            ratio.dadPercent,
            percent
        )
        parentNames.isReader(ContactWindow.SLOT_TWO) -> stringResource(
            R.string.expenses_split_shares_you_second,
            parentNames.labelFor(ContactWindow.SLOT_ONE),
            ratio.momPercent,
            ratio.dadPercent,
            percent
        )
        else -> stringResource(
            R.string.expenses_split_shares_named,
            parentNames.labelFor(ContactWindow.SLOT_ONE),
            ratio.momPercent,
            parentNames.labelFor(ContactWindow.SLOT_TWO),
            ratio.dadPercent,
            percent
        )
    }
}
