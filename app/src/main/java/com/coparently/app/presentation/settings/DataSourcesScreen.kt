package com.coparently.app.presentation.settings

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.IconSizes

/**
 * One source the calendar's holiday data was prepared from, as the data-sources screen lists it.
 *
 * @property titleRes The source's name
 * @property descriptionRes What the app took from it and under which terms, or null when the
 *   row prints its [url] instead (the licence row, whose address *is* the notice)
 * @property url Opened when the row is tapped
 * @property kind Which icon the row carries
 */
internal data class DataSourceNotice(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int?,
    val url: String,
    val kind: Kind
) {
    /** What the row is about, for its icon. */
    enum class Kind { DATASET, LICENCE, REFERENCE, TYPEFACE }
}

/**
 * The third-party data built into the app, and the attribution each one asks for (MON-13).
 *
 * The school-vacation tables for Slovakia (nationwide and per kraj), Austria and the German Länder
 * (`SchoolVacation.kt`, `SlovakHolidays.kt`, `SlovakRegion.kt`, `GermanSchoolVacations.kt`) are
 * read from the OpenHolidays dataset, published under the **Open
 * Database License 1.0**, whose §4.3 asks that a work produced from the database say so, with the
 * licence, wherever a person would expect to find such a notice. That is this screen, reached from
 * Settings → App. The Python `holidays` library (MIT) contributed no code and no data to the app —
 * the public-holiday tables were *checked* against it, date by date, in `HolidayReferenceTest` —
 * so it is named here as a courtesy and because "where do these dates come from" deserves an
 * answer, not because a licence requires it.
 *
 * Kept as data rather than as rows written out in the composable, so the unit test can hold the
 * one thing that must never silently disappear: the ODbL notice and its licence link.
 */
internal object DataSources {

    /** The OpenHolidays project, whose data repository the school vacations were read from. */
    const val OPEN_HOLIDAYS_URL = "https://www.openholidaysapi.org"

    /** The licence text the OpenHolidays data is made available under. */
    const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"

    /** The reference library the public-holiday tables are pinned to. */
    const val HOLIDAYS_LIBRARY_URL = "https://github.com/vacanza/holidays"

    /** The licence the app's typeface, Onest, is distributed under. */
    const val OFL_URL = "https://openfontlicense.org"

    /** Every notice, in the order the screen shows them. */
    val notices: List<DataSourceNotice> = listOf(
        DataSourceNotice(
            titleRes = R.string.data_sources_openholidays_title,
            descriptionRes = R.string.data_sources_openholidays_description,
            url = OPEN_HOLIDAYS_URL,
            kind = DataSourceNotice.Kind.DATASET
        ),
        DataSourceNotice(
            titleRes = R.string.data_sources_odbl_title,
            descriptionRes = null,
            url = ODBL_URL,
            kind = DataSourceNotice.Kind.LICENCE
        ),
        DataSourceNotice(
            titleRes = R.string.data_sources_holidays_title,
            descriptionRes = R.string.data_sources_holidays_description,
            url = HOLIDAYS_LIBRARY_URL,
            kind = DataSourceNotice.Kind.REFERENCE
        )
    )

    /**
     * The typeface bundled in `res/font` (D-8), under a group of its own: it is not holiday data,
     * and the SIL Open Font License asks for its notice to travel with the fonts.
     */
    val typefaceNotices: List<DataSourceNotice> = listOf(
        DataSourceNotice(
            titleRes = R.string.data_sources_onest_title,
            descriptionRes = R.string.data_sources_onest_description,
            url = OFL_URL,
            kind = DataSourceNotice.Kind.TYPEFACE
        )
    )
}

/**
 * Settings → App → *Data sources and licences*: where the calendar's holidays and school
 * vacations come from, and the attribution the OpenHolidays data's licence asks for.
 *
 * A detail screen — the bottom bar hides and an up-arrow returns, like every other Settings
 * destination. No ViewModel: it shows fixed text and opens links.
 *
 * @param onNavigateUp Returns to Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(onNavigateUp: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.data_sources_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                GroupLabel(stringResource(R.string.data_sources_group_holidays))
                SectionGroup {
                    DataSources.notices.forEachIndexed { index, notice ->
                        if (index > 0) Divider()
                        NoticeRow(notice = notice, onOpen = { openExternal(uriHandler, notice.url) })
                    }
                }
            }
            Text(
                text = stringResource(R.string.data_sources_computed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                GroupLabel(stringResource(R.string.data_sources_group_typeface))
                SectionGroup {
                    DataSources.typefaceNotices.forEachIndexed { index, notice ->
                        if (index > 0) Divider()
                        NoticeRow(notice = notice, onOpen = { openExternal(uriHandler, notice.url) })
                    }
                }
            }
        }
    }
}

/**
 * One source: its name, what the app took from it (or the licence's address), and a tap that
 * opens its page.
 *
 * @param notice The source
 * @param onOpen Opens [DataSourceNotice.url]
 */
@Composable
private fun NoticeRow(notice: DataSourceNotice, onOpen: () -> Unit) {
    SectionRow(
        icon = notice.kind.icon(),
        title = stringResource(notice.titleRes),
        supporting = notice.descriptionRes?.let { stringResource(it) } ?: notice.url,
        onClick = onOpen,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    )
}

private fun DataSourceNotice.Kind.icon(): ImageVector = when (this) {
    DataSourceNotice.Kind.DATASET -> Icons.Default.School
    DataSourceNotice.Kind.LICENCE -> Icons.Default.Gavel
    DataSourceNotice.Kind.REFERENCE -> Icons.Default.Event
    DataSourceNotice.Kind.TYPEFACE -> Icons.Default.TextFields
}

private const val TAG = "DataSourcesScreen"

/**
 * Opens [url] in the browser, and does nothing but log when no app can — the same handling
 * `PrivacyPolicyLink.open` documents: Compose's handler throws, and a link must not crash the app.
 */
private fun openExternal(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "No app can open a data-source link", e)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app can open a data-source link", e)
    }
}
