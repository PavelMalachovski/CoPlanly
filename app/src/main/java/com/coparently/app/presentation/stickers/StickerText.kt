package com.coparently.app.presentation.stickers

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import com.coparently.app.R
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.StickerKind

/**
 * How a sticker is worded on the phone that reads it (October 2026), from its kind and its
 * receiver's own [ParentRole] — the one place the app addresses a person as "dad" or "mom".
 * A role nobody gave words it neutrally: the app never guesses one, from a name, a slot or a
 * colour.
 */
object StickerText {

    /** The sentence [kind] says to a receiver whose stickers address them as [role]. */
    @StringRes
    fun sentence(kind: StickerKind, role: ParentRole?): Int = when (kind) {
        StickerKind.GREAT_PARENT -> byRole(
            role,
            R.string.sticker_great_parent_dad,
            R.string.sticker_great_parent_mom,
            R.string.sticker_great_parent_parent
        )
        StickerKind.ON_TIME -> byRole(
            role,
            R.string.sticker_on_time_dad,
            R.string.sticker_on_time_mom,
            R.string.sticker_on_time_parent
        )
        StickerKind.REGULAR -> byRole(
            role,
            R.string.sticker_regular_dad,
            R.string.sticker_regular_mom,
            R.string.sticker_regular_parent
        )
        StickerKind.THANK_YOU -> R.string.sticker_thank_you
        StickerKind.KIDS_HAPPY -> R.string.sticker_kids_happy
        StickerKind.GOOD_TEAM -> R.string.sticker_good_team
        StickerKind.WELL_DONE -> R.string.sticker_well_done
        StickerKind.PATIENCE -> R.string.sticker_patience
        StickerKind.FLEXIBLE -> R.string.sticker_flexible
    }

    /**
     * The phrase as its sender sees it: neutral, because the sender does not read the receiver's
     * role — the receiving phone words it.
     */
    @StringRes
    fun phrase(kind: StickerKind): Int = sentence(kind, ParentRole.PARENT)

    /** How the role itself is named in the question and on the "My stickers" screen. */
    @StringRes
    fun roleName(role: ParentRole): Int = when (role) {
        ParentRole.DAD -> R.string.parent_role_dad
        ParentRole.MOM -> R.string.parent_role_mom
        ParentRole.PARENT -> R.string.parent_role_parent
    }

    /** The picture on [kind]'s badge. */
    fun icon(kind: StickerKind): ImageVector = when (kind) {
        StickerKind.GREAT_PARENT -> Icons.Default.Favorite
        StickerKind.THANK_YOU -> Icons.Default.VolunteerActivism
        StickerKind.KIDS_HAPPY -> Icons.Default.Celebration
        StickerKind.GOOD_TEAM -> Icons.Default.Groups
        StickerKind.WELL_DONE -> Icons.Default.Star
        StickerKind.PATIENCE -> Icons.Default.HourglassTop
        StickerKind.FLEXIBLE -> Icons.Default.SyncAlt
        StickerKind.ON_TIME -> Icons.Default.Schedule
        StickerKind.REGULAR -> Icons.Default.AutoAwesome
    }

    @StringRes
    private fun byRole(role: ParentRole?, @StringRes dad: Int, @StringRes mom: Int, @StringRes parent: Int): Int =
        when (role) {
            ParentRole.DAD -> dad
            ParentRole.MOM -> mom
            ParentRole.PARENT, null -> parent
        }
}
