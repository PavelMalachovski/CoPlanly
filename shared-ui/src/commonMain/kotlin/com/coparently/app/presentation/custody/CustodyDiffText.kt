package com.coparently.app.presentation.custody

import androidx.compose.runtime.Composable
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.resources.Res
import com.coparently.app.resources.custody_diff_child_schedule_changed
import com.coparently.app.resources.custody_diff_days_move
import com.coparently.app.resources.custody_diff_layers_changed
import com.coparently.app.resources.custody_diff_none
import com.coparently.app.resources.custody_diff_windows_only
import com.coparently.app.resources.custody_diff_windows_too
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Turns a [CustodyPatternDiff] into the two lines a parent reads before answering a proposal.
 *
 * In composable scope rather than in the ViewModel for the usual reason: the diff is slots and
 * counts, and only here can the five locales be resolved and a [ParentNames] to turn
 * `"mom"`/`"dad"` into the people they are.
 *
 * @param diff What the proposal would change, or null while nothing has resolved.
 * @param parentNames Resolves a slot to that parent's name.
 * @return The description, or null when there is nothing honest to say — no diff yet, or a
 *   pattern the app could not read. Callers fall back to the plain "a new schedule was
 *   proposed" wording rather than printing a confident "nothing changes".
 */
@Composable
fun custodyDiffDescription(diff: CustodyPatternDiff?, parentNames: ParentNames): String? {
    if (diff == null || !diff.comparable) return null
    if (diff.identical) return stringResource(Res.string.custody_diff_none)
    // A seasonal layer (MON-14) can change without a day moving inside the compared window — a
    // summer proposed in March — so it always gets its own line. So does a child's own schedule
    // (FAM-4): the days counted below are the family's, and one child's move none of them.
    val layersLine = listOfNotNull(
        stringResource(Res.string.custody_diff_layers_changed).takeIf { diff.seasonalLayersChanged },
        stringResource(Res.string.custody_diff_child_schedule_changed).takeIf { diff.childOverridesChanged }
    ).joinToString("\n").ifEmpty { null }
    // Only the afternoons or the layers move: "0 days move" would read as "nothing changes".
    if (diff.movedDays.isEmpty()) {
        val windowsOnly = if (diff.contactWindowsChanged) {
            stringResource(Res.string.custody_diff_windows_only)
        } else {
            null
        }
        return listOfNotNull(windowsOnly, layersLine).joinToString("\n")
    }
    val windowsToo = if (diff.contactWindowsChanged) {
        stringResource(Res.string.custody_diff_windows_too)
    } else {
        null
    }
    val windowsLine = listOfNotNull(windowsToo, layersLine).joinToString("") { "\n$it" }

    val summary = pluralStringResource(
        Res.plurals.custody_diff_days_move,
        diff.movedDayCount,
        diff.movedDayCount
    )

    // Net per slot, in the schema's slot order so the sentence reads the same on both phones.
    val net = listOf("mom", "dad").mapNotNull { slot ->
        diff.netDaysBySlot[slot]?.let { slot to it }
    }
    if (net.isEmpty()) return summary + windowsLine

    val netLine = net.joinToString(" · ") { (slot, days) ->
        // A signed integer with no unit word, deliberately: the plural above already said
        // "days", and "+3"/"-3" needs no grammatical agreement in any of the five locales.
        val signed = if (days > 0) "+$days" else days.toString()
        "${parentNames.labelFor(slot)} $signed"
    }
    return "$summary\n$netLine$windowsLine"
}
