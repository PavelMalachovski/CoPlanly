package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.ConnectivityBanner
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ErrorState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.navigation.CoPlanlyNavigationRail
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.theme.ParentColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared primitives from `DesignSystem.kt` as Settings and the empty lists use them: a
 * labelled `SectionGroup` of `SectionRow`s with each kind of trailing control, and an
 * `EmptyState` with a description and an action.
 *
 * The rows use Settings' own string resources rather than preview literals, so a translation that
 * is too long for its row shows up here.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class DesignSystemScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun settingsFamilyGroup() = snap("settings_family_group") { SettingsFamilyGroupFixture() }

    @Test
    fun emptyState() = snap("common_empty_state") {
        EmptyState(
            icon = Icons.Default.Contacts,
            title = stringResource(R.string.contacts_empty),
            description = stringResource(R.string.contacts_empty_hint),
            actionLabel = stringResource(R.string.contacts_empty_action),
            onAction = {}
        )
    }

    @Test
    fun stickyActionBar() = snap("common_sticky_action_bar") {
        // Disabled, with the notice saying why: the event form's worst case, and the one whose
        // label used to clip at a fixed 52 dp.
        StickyActionBar(
            label = stringResource(R.string.event_form_save),
            onClick = {},
            enabled = false,
            notice = {
                Text(
                    text = stringResource(R.string.event_form_owner_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        )
    }

    @Test
    fun inlineBanner() = snap("common_inline_banner") { InlineBannerFixture() }

    @Test
    fun errorState() = snap("common_error_state") {
        ErrorState(message = stringResource(R.string.profile_load_failed), onRetry = {})
    }

    @Test
    fun connectivityBanner() = snap("common_connectivity_banner") {
        ConnectivityBanner(offline = true)
    }

    /** The rail a wide window gets instead of the bottom bar, on Calendar with Chat badged. */
    @Test
    fun navigationRail() = snap("navigation_rail") {
        Box(modifier = Modifier.height(RAIL_HEIGHT)) {
            CoPlanlyNavigationRail(currentRoute = Screen.Calendar.route, onNavigate = {}, chatUnreadCount = 3)
        }
    }

    companion object {
        private val RAIL_HEIGHT = 420.dp

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}

/** The trailing chevron Settings' navigation rows carry. */
@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Settings' Family group as the screen draws it; shared with [ContrastScreenshots]. */
@Composable
internal fun SettingsFamilyGroupFixture() {
    Column {
        GroupLabel(stringResource(R.string.settings_group_family))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.Group,
                title = stringResource(R.string.settings_pairing_title),
                supporting = stringResource(R.string.settings_pairing_description),
                onClick = {},
                trailing = { Chevron() }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.Diversity3,
                title = stringResource(R.string.friend_section_title),
                supporting = stringResource(R.string.friend_section_supporting),
                onClick = {},
                trailing = { Chevron() }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.FamilyRestroom,
                title = stringResource(R.string.settings_family_kind_title),
                onClick = {},
                trailing = { Chevron() }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(R.string.settings_family_shown),
                onClick = {},
                // A parent-coloured dot: the one place in this group the palette reaches.
                trailing = {
                    PillChip(label = "Pavel", leadingDot = ParentColors.fill("dad"))
                }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_push_notifications),
                supporting = stringResource(R.string.settings_push_notifications_description),
                trailing = { Switch(checked = true, onCheckedChange = {}) }
            )
        }
    }
}

/** The expense split's ask as an [InlineBanner]; shared with [ContrastScreenshots]. */
@Composable
internal fun InlineBannerFixture() {
    // The expense split's ask: a title, the figures and three answers, which wrap onto a
    // second line at large text instead of squeezing their labels.
    InlineBanner(
        title = stringResource(R.string.expenses_split_proposal_title),
        text = stringResource(R.string.expenses_split_proposal_body, 70, 30),
        tone = BannerTone.ATTENTION,
        actions = {
            Button(onClick = {}) { Text(stringResource(R.string.expenses_split_proposal_confirm)) }
            TextButton(onClick = {}) { Text(stringResource(R.string.expenses_split_proposal_decline)) }
            TextButton(onClick = {}) { Text(stringResource(R.string.expenses_split_proposal_later)) }
        }
    )
}
