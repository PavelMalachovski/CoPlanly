package com.coparently.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import java.util.Locale

/**
 * Shared building blocks introduced by the August 2026 design refresh.
 *
 * The refresh replaced six screens' worth of bespoke `Card { ListItem { … } }` stacks with a
 * small set of repeated shapes: a grouped section of rows, a pill-shaped chip, and a labelled
 * group header. They live here rather than in each feature package because the whole point of
 * the pass was that Home, Settings, Expenses and Chat stop inventing their own row anatomy —
 * one definition is what keeps them from drifting apart again.
 *
 * Everything below takes its colours from `MaterialTheme.colorScheme`, never from literal hex,
 * so the same composables render correctly in light and dark.
 */

/** Corner radius of a grouped section container. */
private val GROUP_CORNER = 16.dp

/** Corner radius of a pill chip; large enough to always read as fully rounded. */
private val PILL_CORNER = 16.dp

/**
 * An uppercase label above a [SectionGroup], e.g. "FAMILY".
 *
 * Uppercasing happens here, not in the string resource: a resource is sometimes legitimately
 * shared with a screen that needs it in sentence case (`childinfo_section_*` is also a form
 * header in `AddEditChildInfoScreen`), so baking the casing into the value would leak into that
 * other caller. Centralizing it here means every caller can pass its string as authored and get
 * the same label styling regardless of how that string is cased or reused elsewhere.
 *
 * @param text Label text, in whatever case it is authored; uppercased for display
 * @param modifier Modifier applied to the label
 */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
    )
}

/**
 * A single tonal container holding a run of rows, with hairline dividers between them.
 *
 * This is the refresh's answer to "every list row is a full Card wrapping a ListItem": one
 * surface for the whole group instead of one per row.
 *
 * @param modifier Modifier applied to the container
 * @param content Rows to render; call [SectionGroupScope.Divider] between rows — nothing is
 *   inserted automatically
 */
@Composable
fun SectionGroup(
    modifier: Modifier = Modifier,
    content: @Composable SectionGroupScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            SectionGroupScopeImpl().content()
        }
    }
}

/**
 * Receiver for [SectionGroup] content, used to separate rows with dividers without making
 * every caller remember to add them.
 */
interface SectionGroupScope {
    /**
     * Emits a divider between rows. Call between [SectionRow]s; the last row must not be
     * followed by one.
     */
    @Composable
    fun Divider()
}

private class SectionGroupScopeImpl : SectionGroupScope {
    @Composable
    override fun Divider() {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * One row inside a [SectionGroup]: leading icon, title, optional supporting line, and at most
 * one trailing control.
 *
 * The "at most one trailing control" is the rule the settings audit called for — a row that
 * carries a toggle *and* a chevron *and* two buttons is three interaction models pretending to
 * be one.
 *
 * @param title Primary line
 * @param modifier Modifier applied to the row
 * @param icon Leading icon, or null for a text-only row
 * @param iconTint Tint for [icon]; defaults to the primary colour
 * @param leading A leading slot drawn instead of [icon] — for a row whose subject is a *person*
 *   and whose leading mark is therefore their avatar, not a glyph. Exactly one of the two is
 *   ever drawn: an avatar beside an icon would be the double leading mark this anatomy exists
 *   to prevent.
 * @param supporting Secondary line under [title], or null
 * @param supportingColor Colour of [supporting]; defaults to the muted on-surface variant
 * @param supportingIcon Small status dot colour shown before [supporting], or null for none
 * @param titleColor Colour of [title]; override for destructive rows
 * @param onClick Row tap handler, or null to make the row inert
 * @param trailing The single trailing control (value text, switch, chevron, …)
 */
@Composable
@Suppress("LongParameterList") // one row anatomy, expressed as one parameter list
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingIcon: Color? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (supporting != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (supportingIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(supportingIcon)
                        )
                    }
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

/**
 * A small pill-shaped chip — the refresh's replacement for unlabelled icon actions.
 *
 * Two variants, chosen by [container]: filled when a container colour is given, outlined when
 * it is null. Both keep their label on one line, because a chip that wraps stops reading as a
 * button.
 *
 * @param label Chip text
 * @param modifier Modifier applied to the chip
 * @param icon Optional leading icon
 * @param iconDescription What the icon means, for a screen reader. Null leaves it decorative,
 *   which is right when the icon repeats the label and wrong when it is the only thing saying
 *   something — a status shape, for instance.
 * @param container Fill colour, or null for an outlined chip
 * @param contentColor Text and icon colour
 * @param leadingDot Colour of a small status dot before the label, or null for none
 * @param onClick Tap handler, or null for a display-only chip
 * @param selected Whether this chip is the chosen one of a single-choice set, or null when the
 *   chip is not a choice. Non-null makes it a radio button to TalkBack, which otherwise heard
 *   the selection only as a colour change.
 */
@Composable
@Suppress("LongParameterList") // one chip anatomy, expressed as one parameter list
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    container: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    leadingDot: Color? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null
) {
    val shape = RoundedCornerShape(PILL_CORNER)
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (container != null) Modifier.background(container) else Modifier)
            .then(
                if (container == null) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            // A chip that does something is a control, and a control has to be reachable and
            // announced as one. The padding below puts a pill at roughly 28dp tall — well
            // under the 48dp minimum — and nine call sites are interactive, including the
            // "Review" action on Home's handover card. `Role.Button` is what makes TalkBack
            // say "button" instead of reading the label as ordinary text.
            //
            // Only the interactive branch grows: a decorative chip (a status pill, a category
            // marker) is not a target and padding it to 48dp would wreck the chip strips it
            // sits in.
            .then(
                if (onClick != null) {
                    val action = if (selected != null) {
                        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    }
                    action.defaultMinSize(minHeight = LayoutConstants.MIN_TOUCH_TARGET)
                } else {
                    Modifier
                }
            )
            .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingDot != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(leadingDot)
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * The one empty-state anatomy (UX-9): an icon on a tonal disc, a title, an optional line of
 * explanation and an optional primary action.
 *
 * There used to be six of these — `AnimatedEmptyState` (whose Lottie file had no layers, so it
 * drew a 200 dp blank square above the text) and bespoke columns in Contacts, ChildInfo, Pets,
 * Friends and Home, one of them the `Card { Text }` the refresh outlawed. Two properties are why
 * a shared one is not merely tidier:
 *
 * - **It takes a [modifier] and draws nothing outside it.** The old one hard-coded
 *   `fillMaxSize().padding(32.dp)`, so a caller could not apply its `Scaffold` padding and the
 *   text rendered under the top bar in Chat and Budgets.
 * - **It scrolls when its height is bounded and it does not fit.** At the largest font scale a
 *   title, two lines of explanation and a button outgrow a landscape phone, and a column that
 *   cannot scroll clips the button — the one part a parent needs. Inside a parent that already
 *   scrolls (Home's week card) the height is unbounded and it simply wraps its content, because
 *   a nested vertical scroll measured with infinite height throws.
 *
 * The action is the screen's way out of being empty — "Add a contact", "Add event" — and is
 * omitted where there is none. Design item 8 applies: never pass an action that does nothing.
 *
 * @param icon What the screen would list, drawn decoratively (the title says it in words).
 * @param title One short sentence saying what is missing.
 * @param modifier Applied to the whole state; pass the Scaffold padding and the size here.
 * @param description Optional second line: why it is empty, or what adding one does.
 * @param actionLabel Label of the primary action, or null for none.
 * @param onAction The primary action; the button shows only when this and [actionLabel] are set.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val scrolling = if (constraints.hasBoundedHeight) {
            // At least as tall as the space it was given, so a short state is centred in it,
            // and scrollable, so a tall one is still reachable.
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(scrolling)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(EMPTY_STATE_DISC)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(EMPTY_STATE_ICON)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Diameter of the tonal disc behind an [EmptyState] icon. */
private val EMPTY_STATE_DISC = 72.dp

/** Size of the icon inside the [EmptyState] disc. */
private val EMPTY_STATE_ICON = 36.dp

@LightDarkPreviews
@Composable
private fun EmptyStatePreview() {
    PreviewWrapper {
        EmptyState(
            icon = Icons.Default.Contacts,
            title = "No contacts yet",
            description = "Save the school, the doctor or a relative to reach them in one tap.",
            actionLabel = "Add a contact",
            onAction = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@LightDarkPreviews
@Composable
private fun SectionGroupPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            GroupLabel("Family")
            SectionGroup {
                SectionRow(
                    icon = Icons.Default.Contacts,
                    title = "Contacts",
                    supporting = "3 saved",
                    onClick = {}
                )
                Divider()
                SectionRow(
                    icon = Icons.Default.Balance,
                    title = "Expense split",
                    supporting = "50 / 50",
                    trailing = {
                        PillChip(label = "Agreed", leadingDot = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}
