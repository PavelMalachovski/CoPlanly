package com.coparently.app.presentation.stickers

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
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.StickerKind
import com.coparently.app.resources.Res
import com.coparently.app.resources.parent_role_dad
import com.coparently.app.resources.parent_role_mom
import com.coparently.app.resources.parent_role_parent
import com.coparently.app.resources.sticker_flexible
import com.coparently.app.resources.sticker_good_team
import com.coparently.app.resources.sticker_great_parent_dad
import com.coparently.app.resources.sticker_great_parent_mom
import com.coparently.app.resources.sticker_great_parent_parent
import com.coparently.app.resources.sticker_kids_happy
import com.coparently.app.resources.sticker_on_time_dad
import com.coparently.app.resources.sticker_on_time_mom
import com.coparently.app.resources.sticker_on_time_parent
import com.coparently.app.resources.sticker_patience
import com.coparently.app.resources.sticker_regular_dad
import com.coparently.app.resources.sticker_regular_mom
import com.coparently.app.resources.sticker_regular_parent
import com.coparently.app.resources.sticker_thank_you
import com.coparently.app.resources.sticker_well_done
import org.jetbrains.compose.resources.StringResource

/**
 * How a sticker is worded on the phone that reads it (October 2026), from its kind and its
 * receiver's own [ParentRole] — the one place the app addresses a person as "dad" or "mom".
 * A role nobody gave words it neutrally: the app never guesses one, from a name, a slot or a
 * colour.
 */
object StickerText {

    /** The sentence [kind] says to a receiver whose stickers address them as [role]. */
    fun sentence(kind: StickerKind, role: ParentRole?): StringResource = when (kind) {
        StickerKind.GREAT_PARENT -> byRole(
            role,
            Res.string.sticker_great_parent_dad,
            Res.string.sticker_great_parent_mom,
            Res.string.sticker_great_parent_parent
        )
        StickerKind.ON_TIME -> byRole(
            role,
            Res.string.sticker_on_time_dad,
            Res.string.sticker_on_time_mom,
            Res.string.sticker_on_time_parent
        )
        StickerKind.REGULAR -> byRole(
            role,
            Res.string.sticker_regular_dad,
            Res.string.sticker_regular_mom,
            Res.string.sticker_regular_parent
        )
        StickerKind.THANK_YOU -> Res.string.sticker_thank_you
        StickerKind.KIDS_HAPPY -> Res.string.sticker_kids_happy
        StickerKind.GOOD_TEAM -> Res.string.sticker_good_team
        StickerKind.WELL_DONE -> Res.string.sticker_well_done
        StickerKind.PATIENCE -> Res.string.sticker_patience
        StickerKind.FLEXIBLE -> Res.string.sticker_flexible
    }

    /**
     * The phrase as its sender sees it: neutral, because the sender does not read the receiver's
     * role — the receiving phone words it.
     */
    fun phrase(kind: StickerKind): StringResource = sentence(kind, ParentRole.PARENT)

    /** How the role itself is named in the question and on the "My stickers" screen. */
    fun roleName(role: ParentRole): StringResource = when (role) {
        ParentRole.DAD -> Res.string.parent_role_dad
        ParentRole.MOM -> Res.string.parent_role_mom
        ParentRole.PARENT -> Res.string.parent_role_parent
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

    private fun byRole(
        role: ParentRole?,
        dad: StringResource,
        mom: StringResource,
        parent: StringResource
    ): StringResource =
        when (role) {
            ParentRole.DAD -> dad
            ParentRole.MOM -> mom
            ParentRole.PARENT, null -> parent
        }
}
