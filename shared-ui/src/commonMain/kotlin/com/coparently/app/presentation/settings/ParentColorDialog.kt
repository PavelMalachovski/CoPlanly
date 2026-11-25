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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.CustomHueRange
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.ParentPalette
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.parent_color_clash
import com.coparently.app.resources.parent_color_clash_named
import com.coparently.app.resources.parent_color_custom
import com.coparently.app.resources.parent_color_custom_hue
import com.coparently.app.resources.settings_family_kind_cancel
import com.coparently.app.resources.settings_family_kind_save
import com.coparently.app.resources.settings_parent_color
import org.jetbrains.compose.resources.stringResource
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
 * @param coParent The co-parent's colour, so the picker can say when it would draw this parent's
 *   choice in another colour; null where there is nothing to compare with.
 */
@Composable
internal fun ParentColorDialog(
    selected: ParentColor?,
    fallback: ParentColor,
    onConfirm: (ParentColor) -> Unit,
    onDismiss: () -> Unit,
    coParent: CoParentColour? = null
) {
    val start = selected ?: fallback
    var chosen by rememberSaveable(start.storedCode) { mutableStateOf(start.preset?.name ?: CUSTOM_COLOUR) }
    // The wheel opens on the colour the parent has, so "Custom" starts from where they are.
    var customHue by rememberSaveable(start.storedCode) {
        mutableIntStateOf(CustomHueRange.allowed(start.hue.roundToInt()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_parent_color)) },
        text = {
            // Scrolls so that the slider stays reachable at 200 % text on a short screen.
            ParentColorPicker(
                chosen = chosen,
                customHue = customHue,
                onChoose = { chosen = it },
                onCustomHue = { customHue = it },
                modifier = Modifier.verticalScroll(rememberScrollState()),
                coParent = coParent
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colourFor(chosen, customHue)) }) {
                Text(stringResource(Res.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_family_kind_cancel))
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
 * @param coParent The co-parent's colour. While the calendar would draw this parent's choice in
 *   another colour because it is too close to theirs, a notice above the grid says so, and the
 *   co-parent's first name sits under the swatch of their own preset. Null changes nothing.
 */
@Composable
@Suppress("LongParameterList") // the grid's state and callbacks, a modifier, and the optional co-parent
fun ParentColorPicker(
    chosen: String,
    customHue: Int,
    onChoose: (String) -> Unit,
    onCustomHue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    coParent: CoParentColour? = null
) {
    val clash = coParent?.clashWith(colourFor(chosen, customHue))
    val theirs = coParent?.preset
    Column(modifier = modifier.selectableGroup()) {
        if (clash != null) ColourClashNotice(clash)
        ParentColorChoice.GRID.chunked(GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XS),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { choice ->
                    GridSwatch(
                        label = stringResource(choice.label),
                        swatch = ParentColors.choiceFill(choice),
                        selected = chosen == choice.name,
                        onClick = { onChoose(choice.name) },
                        caption = coParent?.firstName?.takeIf { choice == theirs }
                    )
                }
                // A short last row keeps the columns of the rows above it.
                repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.size(SWATCH_CELL_SIZE)) }
            }
        }
        ColourOption(
            label = stringResource(Res.string.parent_color_custom),
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
 *
 * @param caption A short line under the swatch (the co-parent's first name on the colour they
 *   chose), or null for none.
 */
@Composable
internal fun GridSwatch(
    label: String,
    swatch: Color,
    selected: Boolean,
    onClick: () -> Unit,
    caption: String? = null
) {
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Column(modifier = Modifier.width(SWATCH_CELL_SIZE), horizontalAlignment = Alignment.CenterHorizontally) {
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
                AppIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ParentColors.onFill(swatch),
                    modifier = Modifier.size(IconSizes.Small)
                )
            }
        }
        if (caption != null) {
            // A person's name, not money: it may end in an ellipsis in a cell this narrow.
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val label = stringResource(Res.string.parent_color_custom_hue)
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

/**
 * What the picker needs to know of the co-parent to say that a choice would move.
 *
 * @property name The co-parent's name; only its first word is shown.
 * @property slot The co-parent's slot, `"mom"` or `"dad"`.
 * @property colorCode The co-parent's stored colour, or null when they have not chosen.
 * @property mySlot This parent's slot, which decides who yields when the two are too close.
 */
data class CoParentColour(
    val name: String,
    val slot: String,
    val colorCode: String?,
    val mySlot: String
) {
    /** The first word of [name]: a name is never declined, and a surname adds nothing here. */
    val firstName: String get() = name.trim().substringBefore(' ')

    /** The preset the co-parent chose, or null when they chose none or a colour of their own. */
    internal val preset: ParentColorChoice? get() = ParentColor.fromStored(colorCode)?.preset
}

/**
 * The co-parent as the picker needs them, or null when there is nobody to compare with: either
 * parent unknown or not yet in a slot. A co-parent with no name takes the family's own fallback
 * label.
 */
internal fun coParentColour(parents: Parents, names: ParentNames): CoParentColour? {
    val me = parents.me ?: return null
    val other = parents.coParent ?: return null
    return CoParentColour(
        name = other.name.ifBlank { names.labelForUid(other.uid) },
        slot = other.slot,
        colorCode = other.colorCode,
        mySlot = me.slot
    )
}

/** Why this parent's choice would be drawn in another colour, in the words the picker uses. */
internal sealed interface ColourClash {
    val firstName: String

    /** Both chose the same preset: the sentence names it and the colour this parent gets. */
    data class Same(
        override val firstName: String,
        val theirs: ParentColorChoice,
        val shown: ParentColorChoice
    ) : ColourClash

    /** Close without being the same preset, or a colour from the wheel: no colour is named. */
    data class Close(override val firstName: String) : ColourClash
}

/**
 * The clash between [mine] and the co-parent's colour, or null while the calendar draws [mine] as
 * chosen. Whether it moves is [ParentPalette.shownFor]'s answer, the one rule the calendar uses.
 */
internal fun CoParentColour.clashWith(mine: ParentColor): ColourClash? {
    if (firstName.isBlank()) return null
    val shown = ParentPalette.shownFor(mySlot, mine.storedCode, colorCode)
    if (shown == mine) return null
    val theirs = preset
    val shownPreset = shown.preset
    return if (theirs != null && theirs == mine.preset && shownPreset != null) {
        ColourClash.Same(firstName, theirs, shownPreset)
    } else {
        ColourClash.Close(firstName)
    }
}

/**
 * The notice that says this parent's choice would be drawn in another colour. The picker in
 * Settings and the onboarding's colour step both draw it, so the words and the look are one.
 */
@Composable
internal fun ColourClashNotice(clash: ColourClash, modifier: Modifier = Modifier) {
    InlineBanner(
        text = clash.text(),
        tone = BannerTone.INFO,
        modifier = modifier.padding(bottom = Spacing.S)
    )
}

@Composable
private fun ColourClash.text(): String = when (this) {
    is ColourClash.Same -> stringResource(
        Res.string.parent_color_clash_named,
        firstName,
        stringResource(theirs.label),
        stringResource(shown.label)
    )
    is ColourClash.Close -> stringResource(Res.string.parent_color_clash, firstName)
}

/** The picker's key for "Custom"; a preset is keyed by its own name. */
const val CUSTOM_COLOUR = "CUSTOM"

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
