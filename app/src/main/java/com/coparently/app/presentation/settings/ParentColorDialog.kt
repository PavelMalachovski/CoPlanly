package com.coparently.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.CustomHueRange
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import kotlin.math.roundToInt

/**
 * Picks the colour this parent is drawn in: one of the eighteen grid colours
 * ([ParentColorChoice.GRID]), or any hue on the wheel (October 2026, the owner's "a colour of my
 * own" and "more ready colours, as a grid").
 *
 * Seeded from what this parent actually chose, falling back to the colour their slot has been
 * drawn in all along — never from the co-parent's answer. The same rule the family-kind dialog
 * follows: a control whose Save writes this parent's own record must not open with somebody
 * else's words in it.
 *
 * @param selected This parent's stored colour, or null when they have not chosen.
 * @param fallback What they are drawn in until they choose: their slot's default.
 * @param onConfirm Called with the colour to save.
 * @param onDismiss Called when the dialog is closed without saving.
 */
@Composable
internal fun ParentColorDialog(
    selected: ParentColor?,
    fallback: ParentColor,
    onConfirm: (ParentColor) -> Unit,
    onDismiss: () -> Unit
) {
    val start = selected ?: fallback
    var chosen by rememberSaveable(start.storedCode) { mutableStateOf(start.preset?.name ?: CUSTOM_COLOUR) }
    // The wheel opens on the colour the parent has, so "Custom" starts from where they are.
    var customHue by rememberSaveable(start.storedCode) {
        mutableIntStateOf(CustomHueRange.allowed(start.hue.roundToInt()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_parent_color)) },
        text = {
            // Scrolls so that the slider stays reachable at 200 % text on a short screen.
            ParentColorPicker(
                chosen = chosen,
                customHue = customHue,
                onChoose = { chosen = it },
                onCustomHue = { customHue = it },
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colourFor(chosen, customHue)) }) {
                Text(stringResource(R.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_family_kind_cancel))
            }
        }
    )
}

/**
 * The dialog's body: the grid of [ParentColorChoice.GRID], "Custom", and — while "Custom" is the choice — the
 * slider that picks its hue.
 *
 * Split out of [ParentColorDialog] so a screenshot test can draw it: a dialog opens a window of
 * its own that a node capture does not see.
 *
 * @param chosen A preset's [ParentColorChoice.name], or [CUSTOM_COLOUR].
 * @param customHue The hue "Custom" stands for, in degrees.
 */
@Composable
internal fun ParentColorPicker(
    chosen: String,
    customHue: Int,
    onChoose: (String) -> Unit,
    onCustomHue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        ParentColorChoice.GRID.chunked(GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XS),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { choice ->
                    GridSwatch(
                        label = stringResource(choice.labelRes),
                        swatch = ParentColors.choiceFill(choice),
                        selected = chosen == choice.name,
                        onClick = { onChoose(choice.name) }
                    )
                }
                // A short last row keeps the columns of the rows above it.
                repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.size(SWATCH_CELL_SIZE)) }
            }
        }
        ColourOption(
            label = stringResource(R.string.parent_color_custom),
            swatch = ParentColors.choiceFill(ParentColor.custom(customHue)),
            selected = chosen == CUSTOM_COLOUR,
            onClick = { onChoose(CUSTOM_COLOUR) }
        )
        if (chosen == CUSTOM_COLOUR) {
            HueSlider(hue = customHue, onHue = onCustomHue)
        }
    }
}

/**
 * One option: a radio, the colour as it will be drawn in this theme, and its name — named, not
 * just shown, because a swatch alone is unusable to anyone who cannot tell two of them apart,
 * and a screen reader has nothing else to announce.
 */
@Composable
private fun ColourOption(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .background(swatch)
        )
        Spacer(modifier = Modifier.width(Spacing.M))
        Text(label)
    }
}

/**
 * One colour of the grid: the swatch as it will be drawn in this theme, named for TalkBack — a
 * swatch alone is unusable to anyone who cannot tell two of them apart — and ringed with a check
 * when it is the choice, so the selection does not rest on colour either.
 */
@Composable
private fun GridSwatch(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        modifier = Modifier
            .size(SWATCH_CELL_SIZE)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .border(SELECTED_RING_WIDTH, ring, CircleShape)
            .padding(SELECTED_RING_WIDTH + SELECTED_RING_GAP)
            .clip(CircleShape)
            .background(swatch),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ParentColors.onFill(swatch),
                modifier = Modifier.size(IconSizes.Small)
            )
        }
    }
}

/**
 * The wheel as a strip: every hue a parent may pick, drawn in the tone the calendar will use, with
 * a slider over it. The calendar friend's teal is not on the strip at all
 * ([CustomHueRange.RESERVED]) — the strip runs from green straight to sky blue — so no position
 * can land on it, and there is nothing to refuse.
 */
@Composable
private fun HueSlider(hue: Int, onHue: (Int) -> Unit) {
    val dark = ParentColors.isDarkTheme
    val stops = remember(dark) {
        (0..TRACK_STOPS).map { step ->
            val position = step / TRACK_STOPS.toFloat()
            position to ParentColor.custom(CustomHueRange.hueAt(position)).fillIn(dark)
        }.toTypedArray()
    }
    val label = stringResource(R.string.parent_color_custom_hue)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.S)
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .clip(CoPlanlyCorners.Pill)
                .background(Brush.horizontalGradient(*stops))
        )
        // The slider draws only its thumb: its own track would hide the strip under it.
        Slider(
            value = CustomHueRange.positionOf(hue),
            onValueChange = { onHue(CustomHueRange.hueAt(it)) },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
        )
    }
}

/** What Save writes for [chosen]: the preset of that name, or the custom [hue]. */
private fun colourFor(chosen: String, hue: Int): ParentColor =
    ParentColorChoice.entries.firstOrNull { it.name == chosen }?.color ?: ParentColor.custom(hue)

/** The picker's key for "Custom"; a preset is keyed by its own name. */
internal const val CUSTOM_COLOUR = "CUSTOM"

/** A swatch beside its name, the size the dialog has always drawn. */
private val SWATCH_SIZE = 20.dp

/** Colours per row of the grid: five fit a phone-width dialog at 200 % text, as on the mockup. */
private const val GRID_COLUMNS = 5

/** A grid swatch, its ring included: the 48 dp touch target, so a short row's spacers match it. */
private val SWATCH_CELL_SIZE = 48.dp

/** The ring round the chosen swatch. */
private val SELECTED_RING_WIDTH = 2.dp

/** The surface showing between the ring and the colour, so the ring reads on a dark swatch too. */
private val SELECTED_RING_GAP = 2.dp

/** The strip under the slider: tall enough to read as colour, short of the thumb's height. */
private val TRACK_HEIGHT = 16.dp

/** Gradient stops along the strip; enough that no step between two of them shows. */
private const val TRACK_STOPS = 32
