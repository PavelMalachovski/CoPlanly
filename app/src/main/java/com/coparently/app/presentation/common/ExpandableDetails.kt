package com.coparently.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.coparently.app.R
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing

/**
 * An explanation a screen keeps folded under its action (UX audit M10): a text button naming what
 * it holds, which opens it in place.
 *
 * The calendar feed and the export opened on four and five paragraphs before the one thing either
 * screen does, so a parent scrolled past the reasons to find the button. What must be read before
 * acting stays outside this, on the screen itself; what explains the result goes in here.
 *
 * @param label What the details are about, as the button says it.
 * @param modifier Modifier for the block.
 * @param content The details, laid out in a column.
 */
@Composable
fun ExpandableDetails(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.semantics { stateDescription = state }) {
            Text(label)
            Spacer(Modifier.width(Spacing.XS))
            Icon(
                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(Motion.MEDIUM_MS)) + fadeIn(tween(Motion.MEDIUM_MS)),
            exit = shrinkVertically(tween(Motion.MEDIUM_MS)) + fadeOut(tween(Motion.SHORT_MS))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), content = content)
        }
    }
}
