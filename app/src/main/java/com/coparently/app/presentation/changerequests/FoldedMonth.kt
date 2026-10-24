package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.IconSizes

/**
 * A request card's month, folded behind "Show the month" (second audit: with a whole month on
 * every card the inbox showed about one request a screen, and the parent waiting on the other was
 * five screens down). The card's own lines already say the day; the month is the context a parent
 * opens when they want it.
 *
 * @param model The month to show.
 * @param parentNames Names each parent in the month's legend.
 * @param key What the card is about, so each card remembers its own fold across a rotation.
 */
@Composable
internal fun FoldedMonth(model: MiniMonthModel, parentNames: ParentNames, key: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextButton(onClick = { open = !open }) {
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
            Text(stringResource(if (open) R.string.request_card_hide_month else R.string.request_card_show_month))
        }
        if (open) MiniMonth(model = model, parentNames = parentNames)
    }
}
