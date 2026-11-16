package com.coparently.app.presentation.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.common.optionMenuItems
import com.coparently.app.resources.Res
import com.coparently.app.resources.currency_selector_label
import org.jetbrains.compose.resources.stringResource

/**
 * Compact currency picker shown next to an amount field.
 *
 * @param selected Currently selected currency
 * @param enabled Whether the control accepts input
 * @param onSelect Called with the newly picked currency
 * @param modifier Modifier applied to the root [Box] that anchors the dropdown to the button (the
 *   system's pop-up button on the iPhone)
 * @param background What the selector sits on, behind the iPhone's native button: a card's
 *   `surfaceContainer` by default; a form on the screen's ground passes `colorScheme.background`
 */
@Composable
fun CurrencySelector(
    selected: SupportedCurrency,
    enabled: Boolean,
    onSelect: (SupportedCurrency) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.currency_selector_label)
    val native = nativeControls()

    if (native != null && enabled) {
        // The iPhone: the system's pop-up button with the code, its menu every currency, the chosen
        // one checked. Nothing is laid over Compose content.
        val options = SupportedCurrency.entries.map { it to "${it.code}  ${it.symbol}" }
        native.MenuButton(
            items = optionMenuItems(options, selected, onSelect),
            title = selected.code,
            sfSymbol = null,
            accessibilityLabel = "$label: ${selected.code}",
            tint = Color.Unspecified,
            background = background,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "$label: ${selected.code}" }
        ) {
            Text(selected.code)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SupportedCurrency.entries.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.code}  ${currency.symbol}") },
                    onClick = {
                        onSelect(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}
